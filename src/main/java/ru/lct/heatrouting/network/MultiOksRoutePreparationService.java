package ru.lct.heatrouting.network;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.util.ArrayList;
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

    public MultiOksRoutePreparationService(TerritoryRoutePlanner planner,
                                           ConnectionResolver resolver,
                                           OksConnectionGrouper grouper) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.grouper = Objects.requireNonNull(grouper, "grouper");
    }

    public Preparation prepare(InputDataset metric) {
        Objects.requireNonNull(metric, "metric");
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
            TerritoryRoutePlanner.Route route = planner.find(metric, oks, diameter).orElse(null);
            if (route == null) {
                unconnected.add(oks.getId());
                continue;
            }
            // New chamber IDs are temporary but must not collide between separate tie-ins.
            ConnectionResolver.Connection connection = resolver.resolve(metric, route,
                    "v1_chamber_oks_" + oks.getId());
            if (connection.isExistingChamber()
                    && route.getGeometry().getEndPoint().distance(connection.getPoint())
                    > ENDPOINT_TOLERANCE_METERS) {
                Point chamber = connection.getPoint();
                route = planner.findTo(metric, oks, diameter, chamber,
                        connection.getExistingSegmentId()).orElse(null);
                if (route == null) {
                    unconnected.add(oks.getId());
                    continue;
                }
                connection = resolver.resolve(metric, route,
                        "v1_chamber_oks_" + oks.getId());
            }
            prepared.add(new PreparedConnection(oks, route, connection, diameter));
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
