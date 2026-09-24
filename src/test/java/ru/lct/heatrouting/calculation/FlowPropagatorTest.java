package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowPropagatorTest {
    private final GeometryFactory factory = new GeometryFactory();

    private Node node(String id, double x, double y) {
        return new Node(id, factory.createPoint(new Coordinate(x, y)), "heat_chamber");
    }

    private Edge edge(String id, Node from, Node to) {
        return new Edge(id, from, to,
                factory.createLineString(new Coordinate[]{from.getPoint().getCoordinate(),
                        to.getPoint().getCoordinate()}), 100, 0, "base");
    }

    @Test
    void sumsTwoOksOnSharedSegmentWithoutMutatingEdges() {
        Node root = node("root", 0, 0);
        Node junction = node("junction", 1, 0);
        Node a = node("a", 2, 1);
        Node b = node("b", 2, -1);
        Edge shared = edge("shared", junction, root);
        Map<String, Double> result = new FlowPropagator().propagate(root,
                Map.of(junction, shared, a, edge("a", a, junction),
                        b, edge("b", b, junction)), Map.of(a, 3.5, b, 8.0));
        assertEquals(11.5, result.get("shared"));
        assertEquals(3.5, result.get("a"));
        assertEquals(8.0, result.get("b"));
        assertEquals(0, shared.getFlowTph());
    }

    @Test
    void rejectsCycle() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);
        Node b = node("b", 2, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(root,
                        Map.of(a, edge("a", a, b), b, edge("b", b, a)), Map.of(a, 1.0)));
    }
}
