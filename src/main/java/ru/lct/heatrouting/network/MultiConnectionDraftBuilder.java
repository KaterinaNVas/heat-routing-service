package ru.lct.heatrouting.network;

import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One tree per physical tie-in. Shared route geometry requires explicit topology. */
@Component
public class MultiConnectionDraftBuilder {
    private static final double TOLERANCE = 0.01;

    public List<Draft> build(MultiOksRoutePreparationService.Preparation preparation) {
        Objects.requireNonNull(preparation, "preparation");
        List<Draft> result = new ArrayList<>();
        int groupNumber = 0;
        for (List<MultiOksRoutePreparationService.PreparedConnection> group : preparation.getGroups()) {
            if (group.isEmpty()) throw new IllegalArgumentException("Пустая группа ОКС");
            groupNumber++;
            ConnectionResolver.Connection tieIn = group.get(0).getConnection();
            Node root = new Node(tieIn.getChamberId(), tieIn.getPoint(), "heat_chamber");
            Map<Node, Edge> parentEdge = new LinkedHashMap<>();
            Map<Node, Double> demands = new LinkedHashMap<>();
            List<LineString> routes = new ArrayList<>();
            int routeNumber = 0;
            for (MultiOksRoutePreparationService.PreparedConnection item : group) {
                routeNumber++;
                ConnectionResolver.Connection connection = item.getConnection();
                if (connection.isExistingChamber() != tieIn.isExistingChamber()
                        || connection.getPoint().distance(root.getPoint()) > TOLERANCE
                        || (tieIn.isExistingChamber() && !tieIn.getChamberId().equals(connection.getChamberId()))
                        || (!tieIn.isExistingChamber() && !tieIn.getExistingSegmentId().equals(connection.getExistingSegmentId()))) {
                    throw new IllegalArgumentException("В группе разные точки подключения");
                }
                ConnectionPoint oks = item.getOks();
                LineString route = item.getRoute().getGeometry();
                Double flow = oks.getFlowTph();
                if (oks.getId() == null || oks.getGeometry() == null
                        || flow == null || !Double.isFinite(flow) || flow <= 0
                        || route == null || route.getSRID() != 32637
                        || item.getProvisionalDiameter() <= 0
                        || route.getStartPoint().distance(oks.getGeometry()) > TOLERANCE
                        || route.getEndPoint().distance(root.getPoint()) > TOLERANCE
                        || route.getLength() <= TOLERANCE) {
                    throw new IllegalArgumentException("Некорректный маршрут ОКС " + oks.getId());
                }
                // Overlapping lines are the same pipe, not two independent construction items.
                // A junction must be created there before the variant can be priced.
                for (LineString previous : routes) {
                    if (previous.intersection(route).getLength() > TOLERANCE) {
                        throw new IllegalArgumentException(
                                "Маршруты имеют общий участок: требуется узел и одно общее ребро");
                    }
                }
                routes.add(route);
                Node oksNode = new Node("oks:" + oks.getId(), oks.getGeometry(), "oks_connection_point");
                if (demands.putIfAbsent(oksNode, flow) != null) {
                    throw new IllegalArgumentException("Повтор ОКС: " + oks.getId());
                }
                Edge edge = new Edge("multi_g" + groupNumber + "_e" + routeNumber,
                        oksNode, root, route, item.getProvisionalDiameter(), 0, "base");
                parentEdge.put(oksNode, edge);
            }
            result.add(new Draft(root, parentEdge, demands, tieIn));
        }
        return List.copyOf(result);
    }

    public static final class Draft {
        private final Node root;
        private final Map<Node, Edge> parentEdge;
        private final Map<Node, Double> demands;
        private final ConnectionResolver.Connection connection;

        private Draft(Node root, Map<Node, Edge> parentEdge, Map<Node, Double> demands,
                      ConnectionResolver.Connection connection) {
            this.root = root;
            this.parentEdge = Map.copyOf(parentEdge);
            this.demands = Map.copyOf(demands);
            this.connection = connection;
        }

        public Node getRoot() { return root; }
        public Map<Node, Edge> getParentEdge() { return parentEdge; }
        public Map<Node, Double> getDemands() { return demands; }
        public ConnectionResolver.Connection getConnection() { return connection; }
    }
}
