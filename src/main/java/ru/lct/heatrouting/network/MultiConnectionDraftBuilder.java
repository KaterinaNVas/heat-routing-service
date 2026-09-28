package ru.lct.heatrouting.network;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.geo.RouteAngleValidator;
import ru.lct.heatrouting.geo.RouteValidationResult;
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
    private final RouteAngleValidator angleValidator = new RouteAngleValidator();
    private final GeometryFactory geometryFactory = new GeometryFactory();

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
            List<MultiOksRoutePreparationService.PreparedConnection> valid = new ArrayList<>();
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
                LineString construction = route;
                Double flow = oks.getFlowTph();
                if (oks.getId() == null || oks.getGeometry() == null
                        || flow == null || !Double.isFinite(flow) || flow <= 0
                        || route == null || route.getSRID() != 32637
                        || construction == null || construction.getSRID() != 32637
                        || item.getProvisionalDiameter() <= 0
                        || route.getStartPoint().distance(oks.getGeometry()) > TOLERANCE
                        || construction.getEndPoint().distance(route.getEndPoint()) > TOLERANCE
                        || route.getEndPoint().distance(root.getPoint()) > TOLERANCE
                        || construction.getLength() <= TOLERANCE) {
                    throw new IllegalArgumentException("Некорректный маршрут ОКС " + oks.getId());
                }
                RouteValidationResult validation = angleValidator.validate(route);
                if (!validation.isValid()) {
                    throw new IllegalArgumentException("Недопустимый маршрут ОКС "
                            + oks.getId() + ": " + validation.getErrors());
                }
                // Overlapping lines are the same pipe, not two independent construction items.
                // A junction must be created there before the variant can be priced.
                routes.add(construction);
                valid.add(item);
                Node oksNode = new Node("oks:" + oks.getId(),
                        oks.getGeometry(), "oks_connection_point");
                if (demands.putIfAbsent(oksNode, flow) != null) {
                    throw new IllegalArgumentException("Повтор ОКС: " + oks.getId());
                }
            }
            buildEdges(groupNumber, valid, routes, root, parentEdge);
            result.add(new Draft(root, parentEdge, demands, tieIn));
        }
        return List.copyOf(result);
    }

    private void buildEdges(int groupNumber,
                            List<MultiOksRoutePreparationService.PreparedConnection> items,
                            List<LineString> routes, Node root, Map<Node, Edge> edges) {
        // The trie is rooted at the tie-in. Equal route suffixes map to one physical pipe.
        Branch top = new Branch(root.getPoint().getCoordinate());
        List<Branch> leaves = new ArrayList<>();
        for (LineString route : routes) {
            Branch current = top;
            for (int i = route.getNumPoints() - 2; i >= 0; i--) {
                Coordinate coordinate = route.getCoordinateN(i);
                Branch next = null;
                for (Branch child : current.children) {
                    if (child.coordinate.distance(coordinate) <= TOLERANCE) {
                        next = child;
                        break;
                    }
                }
                if (next == null) {
                    next = new Branch(coordinate);
                    next.parent = current;
                    current.children.add(next);
                }
                current = next;
            }
            if (current.oks != null || !current.children.isEmpty()) {
                throw new IllegalArgumentException("Маршруты имеют общий участок без отдельного отвода к ОКС");
            }
            current.oks = items.get(leaves.size());
            leaves.add(current);
        }
        int[] nextId = {0};
        for (Branch leaf : leaves) {
            MultiOksRoutePreparationService.PreparedConnection item = leaf.oks;
            Node child = new Node("oks:" + item.getOks().getId(),
                    item.getOks().getGeometry(), "oks_connection_point");
            Branch branch = leaf;
            while (branch != top) {
                List<Coordinate> coordinates = new ArrayList<>();
                coordinates.add(branch.coordinate);
                Branch cursor = branch.parent;
                while (cursor != top && cursor.children.size() == 1) {
                    coordinates.add(cursor.coordinate);
                    cursor = cursor.parent;
                }
                coordinates.add(cursor.coordinate);
                Node parent = cursor == top ? root : cursor.node;
                if (parent == null) {
                    Point point = geometryFactory.createPoint(cursor.coordinate);
                    point.setSRID(32637);
                    parent = new Node("multi_g" + groupNumber + "_junction_" + (++nextId[0]),
                            point, "heat_chamber");
                    cursor.node = parent;
                }
                if (edges.containsKey(child)) break; // common suffix already emitted
                LineString line = geometryFactory.createLineString(coordinates.toArray(new Coordinate[0]));
                line.setSRID(32637);
                if (line.getLength() <= TOLERANCE) throw new IllegalArgumentException("Нулевой участок сети");
                String id = "multi_g" + groupNumber + "_e" + (++nextId[0]);
                edges.put(child, new Edge(id, child, parent, line,
                        item.getProvisionalDiameter(), 0, "base"));
                child = parent;
                branch = cursor;
            }
        }
        // Reject overlaps that are not identical route suffixes. They need a separately
        // located junction, which cannot safely be inferred from this vertex trie.
        List<Edge> built = new ArrayList<>(edges.values());
        for (int i = 0; i < built.size(); i++) {
            for (int j = i + 1; j < built.size(); j++) {
                if (built.get(i).getGeometry().intersection(built.get(j).getGeometry())
                        .getLength() > TOLERANCE) {
                    throw new IllegalArgumentException("Маршруты имеют общий участок без узла ветвления");
                }
            }
        }
    }

    private static final class Branch {
        private final Coordinate coordinate;
        private final List<Branch> children = new ArrayList<>();
        private Branch parent;
        private Node node;
        private MultiOksRoutePreparationService.PreparedConnection oks;
        private Branch(Coordinate coordinate) { this.coordinate = coordinate; }
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
