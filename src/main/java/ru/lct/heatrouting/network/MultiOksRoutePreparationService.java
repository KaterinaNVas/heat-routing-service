package ru.lct.heatrouting.network;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.lct.heatrouting.geo.RouteAngleValidator;
import ru.lct.heatrouting.calculation.SpecialCrossingCostCalculator;
import ru.lct.heatrouting.calculation.SpecialCrossingProcessor;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Prepares routes and tie-ins for the multi-OKS draft builder. Input is EPSG:32637. */
@Service
public class MultiOksRoutePreparationService {
    private static final double ENDPOINT_TOLERANCE_METERS = 0.01;

    private final TerritoryRoutePlanner planner;
    private final ConnectionResolver resolver;
    private final OksConnectionGrouper grouper;
    private final DiameterCatalog diameters = new DiameterCatalog();
    private final SpecialCrossingProcessor crossings = new SpecialCrossingProcessor();
    private final RouteAngleValidator angles = new RouteAngleValidator();
    private final SpecialCrossingCostCalculator crossingCosts = new SpecialCrossingCostCalculator();
    private final VariantCalculator variantCosts = new VariantCalculator(0.7, 0.3);

    public enum RoutePreference { SHORTEST, LOWEST_STANDALONE_COST }

    public MultiOksRoutePreparationService(TerritoryRoutePlanner planner,
                                           ConnectionResolver resolver,
                                           OksConnectionGrouper grouper) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.grouper = Objects.requireNonNull(grouper, "grouper");
    }

    public Preparation prepare(InputDataset metric) {
        return prepare(metric, RoutePreference.SHORTEST);
    }

    public Preparation prepare(InputDataset metric, RoutePreference preference) {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(preference, "preference");
        List<PreparedConnection> prepared = new ArrayList<>();
        List<String> unconnected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ConnectionPoint oks : metric.getConnectionPoints()) {
            if (oks == null || oks.getId() == null || !ids.add(oks.getId())
                    || oks.getFlowTph() == null || !Double.isFinite(oks.getFlowTph())
                    || oks.getFlowTph() <= 0.0) {
                throw new IllegalArgumentException("Повтор id ОКС или неверный расход");
            }
            int diameter = diameters.findMinimalDiameter(oks.getFlowTph());
            List<TerritoryRoutePlanner.Route> candidates = preference == RoutePreference.SHORTEST
                    ? planner.find(metric, oks, diameter).map(List::of).orElseGet(List::of)
                    : planner.findAlternatives(metric, oks, diameter, 3);
            List<PreparedConnection> valid = new ArrayList<>();
            for (TerritoryRoutePlanner.Route candidate : candidates) {
                PreparedConnection selected = prepareCandidate(metric, oks, diameter, candidate);
                if (selected != null) valid.add(selected);
            }
            if (valid.isEmpty()) {
                unconnected.add(oks.getId());
                continue;
            }
            PreparedConnection best = preference == RoutePreference.SHORTEST ? valid.get(0)
                    : valid.stream().min(Comparator.comparingDouble(entry ->
                            standaloneCost(entry, metric))).orElseThrow();
            prepared.add(best);
        }

        redistributeOverloadedChambers(metric, prepared, unconnected);

        List<OksConnectionGrouper.Candidate> candidates = new ArrayList<>();
        Map<String, PreparedConnection> byOksId = new HashMap<>();
        for (PreparedConnection entry : prepared) {
            candidates.add(new OksConnectionGrouper.Candidate(
                    entry.oks.getId(), entry.connection));
            byOksId.put(entry.oks.getId(), entry);
        }
        List<List<PreparedConnection>> groups = new ArrayList<>();
        for (List<OksConnectionGrouper.Candidate> candidateGroup : grouper.group(candidates)) {
            List<PreparedConnection> group = new ArrayList<>();
            for (OksConnectionGrouper.Candidate candidate : candidateGroup) {
                group.add(byOksId.get(candidate.getOksId()));
            }
            groups.add(List.copyOf(group));
        }
        return new Preparation(groups, unconnected);
    }

    private void redistributeOverloadedChambers(InputDataset metric,
                                                 List<PreparedConnection> prepared,
                                                 List<String> unconnected) {
        Map<String, Integer> assigned = new HashMap<>();
        for (PreparedConnection item : prepared) {
            if (item.connection.isExistingChamber()) {
                assigned.merge(item.connection.getChamberId(), 1, Integer::sum);
            }
        }
        Map<String, PreparedConnection> alternatives = new HashMap<>();
        for (;;) {
            PreparedConnection bestOriginal = null;
            PreparedConnection bestAlternative = null;
            double bestExtraCost = Double.POSITIVE_INFINITY;
            for (PreparedConnection original : prepared) {
                if (!original.connection.isExistingChamber()) continue;
                String sourceId = original.connection.getChamberId();
                if (assigned.getOrDefault(sourceId, 0)
                        <= availableNewPipes(metric, sourceId)) continue;
                List<ru.lct.heatrouting.model.HeatChamber> chambers = new ArrayList<>(metric.getHeatChambers());
                chambers.sort(Comparator.comparingDouble(chamber ->
                        chamber.getGeometry().distance(original.oks.getGeometry())));
                int considered = 0;
                for (ru.lct.heatrouting.model.HeatChamber chamber : chambers) {
                    String destinationId = chamber.getId();
                    if (sourceId.equals(destinationId) || assigned.getOrDefault(destinationId, 0)
                            >= availableNewPipes(metric, destinationId)) continue;
                    if (++considered > 5) break;
                    String key = original.oks.getId() + "@" + destinationId;
                    PreparedConnection candidate;
                    if (alternatives.containsKey(key)) {
                        candidate = alternatives.get(key);
                    } else {
                        candidate = alternativeTo(metric, original, chamber);
                        alternatives.put(key, candidate);
                    }
                    if (candidate == null) continue;
                    double extra = standaloneCost(candidate, metric) - standaloneCost(original, metric);
                    if (extra < bestExtraCost) {
                        bestExtraCost = extra;
                        bestOriginal = original;
                        bestAlternative = candidate;
                    }
                }
            }
            if (bestOriginal == null) {
                PreparedConnection overflow = prepared.stream()
                        .filter(item -> item.connection.isExistingChamber()
                                && assigned.getOrDefault(item.connection.getChamberId(), 0)
                                > availableNewPipes(metric, item.connection.getChamberId()))
                        .findFirst().orElse(null);
                if (overflow == null) return;
                // No admissible chamber is available: leave the OKS disconnected and
                // let the normal variant calculation apply its specified penalty.
                prepared.remove(overflow);
                unconnected.add(overflow.oks.getId());
                assigned.merge(overflow.connection.getChamberId(), -1, Integer::sum);
                continue;
            }
            int index = prepared.indexOf(bestOriginal);
            prepared.set(index, bestAlternative);
            assigned.merge(bestOriginal.connection.getChamberId(), -1, Integer::sum);
            assigned.merge(bestAlternative.connection.getChamberId(), 1, Integer::sum);
        }
    }

    private PreparedConnection alternativeTo(InputDataset metric, PreparedConnection original,
                                              ru.lct.heatrouting.model.HeatChamber chamber) {
        for (ru.lct.heatrouting.model.HeatNetworkSegment segment : metric.getHeatNetwork()) {
            if (segment.getGeometry().distance(chamber.getGeometry()) > ENDPOINT_TOLERANCE_METERS)
                continue;
            TerritoryRoutePlanner.Route route = planner.findTo(metric, original.oks,
                    original.provisionalDiameter, chamber.getGeometry(), segment.getId()).orElse(null);
            if (route == null) continue;
            PreparedConnection candidate = prepareCandidate(metric, original.oks,
                    original.provisionalDiameter, route);
            if (candidate != null && candidate.connection.isExistingChamber()
                    && chamber.getId().equals(candidate.connection.getChamberId())) return candidate;
        }
        return null;
    }

    private int availableNewPipes(InputDataset metric, String chamberId) {
        ru.lct.heatrouting.model.HeatChamber chamber = metric.getHeatChambers().stream()
                .filter(item -> chamberId.equals(item.getId())).findFirst().orElse(null);
        if (chamber == null) return 0;
        int existing = 0;
        for (ru.lct.heatrouting.model.HeatNetworkSegment segment : metric.getHeatNetwork()) {
            if (segment.getGeometry().distance(chamber.getGeometry()) > ENDPOINT_TOLERANCE_METERS)
                continue;
            boolean end = segment.getGeometry().getStartPoint().distance(chamber.getGeometry())
                    <= ENDPOINT_TOLERANCE_METERS || segment.getGeometry().getEndPoint()
                    .distance(chamber.getGeometry()) <= ENDPOINT_TOLERANCE_METERS;
            existing += end ? 1 : 2;
        }
        return Math.max(0, 4 - existing);
    }

    private PreparedConnection prepareCandidate(InputDataset metric, ConnectionPoint oks,
                                                 int diameter, TerritoryRoutePlanner.Route route) {
        // New chamber IDs are temporary but must not collide between separate tie-ins.
        ConnectionResolver.Connection connection = resolver.resolve(metric, route,
                "v1_chamber_oks_" + oks.getId());
        if (connection.isExistingChamber()
                && route.getGeometry().getEndPoint().distance(connection.getPoint())
                > ENDPOINT_TOLERANCE_METERS) {
            Point chamber = connection.getPoint();
            String segmentId = connection.getExistingSegmentId();
            boolean chamberOnSelectedSegment = metric.getHeatNetwork().stream()
                    .filter(segment -> segment.getId().equals(segmentId))
                    .anyMatch(segment -> segment.getGeometry().distance(chamber)
                            <= ENDPOINT_TOLERANCE_METERS);
            if (!chamberOnSelectedSegment) return null;
            route = planner.findTo(metric, oks, diameter, chamber,
                    segmentId).orElse(null);
            if (route == null) return null;
            connection = resolver.resolve(metric, route,
                    "v1_chamber_oks_" + oks.getId());
        }
        try {
            crossings.validateAngles(route.getGeometry(), metric.getRestrictions());
            crossingCosts.validateStraightSpecialPasses(route.getGeometry(), metric.getRestrictions());
        } catch (IllegalStateException invalidCrossing) {
            return null;
        }
        if (!angles.validate(route.getGeometry()).isValid()) return null;
        return new PreparedConnection(oks, route, connection, diameter);
    }

    private double standaloneCost(PreparedConnection entry, InputDataset metric) {
        double length = entry.route.getGeometry().getLength();
        double pipes = crossingCosts.calculate(new NewSegment("candidate", entry.provisionalDiameter,
                length), entry.route.getGeometry(), metric.getRestrictions());
        double chamber = entry.connection.isExistingChamber() ? 5_000_000
                : variantCosts.chamberCost(entry.provisionalDiameter);
        return pipes + chamber;
    }

    public static final class PreparedConnection {
        private final ConnectionPoint oks;
        private final TerritoryRoutePlanner.Route route;
        private final ConnectionResolver.Connection connection;
        private final int provisionalDiameter;

        private PreparedConnection(ConnectionPoint oks, TerritoryRoutePlanner.Route route,
                                   ConnectionResolver.Connection connection,
                                   int provisionalDiameter) {
            this.oks = oks;
            this.route = route;
            this.connection = connection;
            this.provisionalDiameter = provisionalDiameter;
        }

        public ConnectionPoint getOks() { return oks; }
        public TerritoryRoutePlanner.Route getRoute() { return route; }
        public ConnectionResolver.Connection getConnection() { return connection; }
        public int getProvisionalDiameter() { return provisionalDiameter; }
    }

    public static final class Preparation {
        private final List<List<PreparedConnection>> groups;
        private final List<String> unconnectedOksIds;

        private Preparation(List<List<PreparedConnection>> groups,
                            List<String> unconnectedOksIds) {
            this.groups = List.copyOf(groups);
            this.unconnectedOksIds = List.copyOf(unconnectedOksIds);
        }

        public List<List<PreparedConnection>> getGroups() { return groups; }
        public List<String> getUnconnectedOksIds() { return unconnectedOksIds; }
    }
}
