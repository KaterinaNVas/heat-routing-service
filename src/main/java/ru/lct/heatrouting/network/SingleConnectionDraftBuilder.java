package ru.lct.heatrouting.network;

import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.util.Map;
import java.util.Objects;

/** Переход от маршрута одного ОКС к топологии для FlowPropagator. */
@Component
public class SingleConnectionDraftBuilder {
    private static final double ENDPOINT_TOLERANCE_METERS = 0.01;

    public Draft build(ConnectionPoint oks, TerritoryRoutePlanner.Route route,
                       ConnectionResolver.Connection connection, String newSegmentId,
                       int provisionalDiameter) {
        Objects.requireNonNull(oks, "oks");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(connection, "connection");
        LineString geometry = route.getGeometry();
        if (oks.getGeometry() == null || oks.getFlowTph() == null ||
                !Double.isFinite(oks.getFlowTph()) || oks.getFlowTph() < 0 ||
                geometry == null || geometry.getSRID() != 32637 || provisionalDiameter <= 0) {
            throw new IllegalArgumentException("Некорректные данные маршрута или расход ОКС");
        }
        if (geometry.getStartPoint().distance(oks.getGeometry()) > ENDPOINT_TOLERANCE_METERS) {
            throw new IllegalArgumentException("Маршрут должен начинаться в точке ОКС");
        }
        if (geometry.getEndPoint().distance(connection.getPoint()) > ENDPOINT_TOLERANCE_METERS) {
            throw new IllegalArgumentException(
                    "Маршрут должен заканчиваться в выбранной камере; нужно перестроить путь к ней");
        }
        Node oksNode = new Node(oks.getId(), oks.getGeometry(), "oks_connection_point");
        Node root = new Node(connection.getChamberId(), connection.getPoint(), "heat_chamber");
        Edge edge = new Edge(Objects.requireNonNull(newSegmentId, "newSegmentId"),
                oksNode, root, geometry, provisionalDiameter, 0.0, "base");
        return new Draft(root, Map.of(oksNode, edge), Map.of(oksNode, oks.getFlowTph()), connection);
    }

    public static final class Draft {
        private final Node root;
        private final Map<Node, Edge> parentEdge;
        private final Map<Node, Double> demands;
        private final ConnectionResolver.Connection connection;

        private Draft(Node root, Map<Node, Edge> parentEdge, Map<Node, Double> demands,
                      ConnectionResolver.Connection connection) {
            this.root = root;
            this.parentEdge = parentEdge;
            this.demands = demands;
            this.connection = connection;
        }
        public Node getRoot() { return root; }
        public Map<Node, Edge> getParentEdge() { return parentEdge; }
        public Map<Node, Double> getDemands() { return demands; }
        public ConnectionResolver.Connection getConnection() { return connection; }
    }
}
