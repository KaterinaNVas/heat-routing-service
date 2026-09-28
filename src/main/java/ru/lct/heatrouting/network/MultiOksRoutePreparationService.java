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
    private final VariantCalculator variantCosts = new VariantCalculator(0.3, 0.7);

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
        } catch (IllegalStateException invalidCrossing) {
            return null;
        }
        if (!angles.validate(route.getGeometry()).isValid()) return null;
        return new PreparedConnection(oks, route, connection, diameter);
    }

    private double standaloneCost(PreparedConnection entry, InputDataset metric) {
        double length = entry.route.getConstructionGeometry().getLength();
        double pipes = crossingCosts.calculate(new NewSegment("candidate", entry.provisionalDiameter,
                length), entry.route.getConstructionGeometry(), metric.getRestrictions());
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
