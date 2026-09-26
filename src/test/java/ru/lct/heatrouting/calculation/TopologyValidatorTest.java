package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TopologyValidatorTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final TopologyValidator validator = new TopologyValidator();

    private Node node(String id, double x, String type) {
        return new Node(id, factory.createPoint(new Coordinate(x, 0)), type);
    }

    private Edge edge(String id, Node from, Node to, int dn) {
        return new Edge(id, from, to,
            factory.createLineString(new Coordinate[]{
                from.getPoint().getCoordinate(), to.getPoint().getCoordinate()
            }), dn, 0, "base");
    }

    // ===== validateDiameterMonotonicity =====

    @Test
    void acceptsMonotonicDiameters() {
        Node root = node("root", 0, "heat_chamber");
        Node mid = node("mid", 1, "heat_chamber");
        Node oks = node("oks", 2, "oks_connection_point");

        Edge e1 = edge("e1", mid, root, 200);
        Edge e2 = edge("e2", oks, mid, 100);

        validator.validateDiameterMonotonicity(root,
            Map.of(mid, e1, oks, e2), Map.of(oks, 50.0));
    }

    @Test
    void rejectsDiameterDecreasing() {
        Node root = node("root", 0, "heat_chamber");
        Node mid = node("mid", 1, "heat_chamber");
        Node oks = node("oks", 2, "oks_connection_point");

        Edge e1 = edge("e1", mid, root, 100);
        Edge e2 = edge("e2", oks, mid, 200);  // нарушение

        assertThrows(IllegalStateException.class,
            () -> validator.validateDiameterMonotonicity(root,
                Map.of(mid, e1, oks, e2), Map.of(oks, 50.0)));
    }

    @Test
    void rejectsIfOksHasNoPath() {
        Node root = node("root", 0, "heat_chamber");
        Node oks = node("oks", 2, "oks_connection_point");

        assertThrows(IllegalArgumentException.class,
            () -> validator.validateDiameterMonotonicity(root,
                Map.of(), Map.of(oks, 50.0)));
    }

    @Test
    void rejectsNullArguments() {
        Node root = node("root", 0, "heat_chamber");
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateDiameterMonotonicity(null, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateDiameterMonotonicity(root, null, Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateDiameterMonotonicity(root, Map.of(), null));
    }

    // ===== validateBranchingsInChambers =====

    @Test
    void acceptsBranchingInChamber() {
        Node root = node("root", 0, "heat_chamber");
        Node junction = node("j", 1, "heat_chamber");
        Node a = node("a", 2, "oks_connection_point");
        Node b = node("b", 2, "oks_connection_point");

        Edge e1 = edge("e1", junction, root, 200);
        Edge e2 = edge("e2", a, junction, 100);
        Edge e3 = edge("e3", b, junction, 100);

        validator.validateBranchingsInChambers(
            Map.of(junction, e1, a, e2, b, e3));
    }

    @Test
    void rejectsBranchingOutsideChamber() {
        Node root = node("root", 0, "heat_chamber");
        Node junction = node("j", 1, "oks_connection_point");  // ← не камера
        Node a = node("a", 2, "oks_connection_point");
        Node b = node("b", 2, "oks_connection_point");

        Edge e1 = edge("e1", junction, root, 200);
        Edge e2 = edge("e2", a, junction, 100);
        Edge e3 = edge("e3", b, junction, 100);

        assertThrows(IllegalStateException.class,
            () -> validator.validateBranchingsInChambers(
                Map.of(junction, e1, a, e2, b, e3)));
    }

    @Test
    void acceptsLinearChain() {
        Node root = node("root", 0, "heat_chamber");
        Node a = node("a", 1, "heat_chamber");
        Node b = node("b", 2, "oks_connection_point");

        Edge e1 = edge("e1", a, root, 200);
        Edge e2 = edge("e2", b, a, 100);

        validator.validateBranchingsInChambers(Map.of(a, e1, b, e2));
    }

    @Test
    void rejectsNullParentEdge() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateBranchingsInChambers(null));
    }
}
