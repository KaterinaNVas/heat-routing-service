package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

    // ===== Существующие тесты =====

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
    void returnsAllTreeEdgesWithZeroFlowWhenThereAreNoDemands() {
        Node root = node("root", 0, 0);
        Node junction = node("junction", 1, 0);
        Node oks = node("oks", 2, 0);
        Edge shared = edge("shared", junction, root);
        Edge branch = edge("branch", oks, junction);

        Map<String, Double> result = new FlowPropagator().propagate(root,
                Map.of(junction, shared, oks, branch), Map.of());

        assertEquals(Map.of("shared", 0.0, "branch", 0.0), result);
        assertEquals(0.0, shared.getFlowTph());
        assertEquals(0.0, branch.getFlowTph());
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

    // ===== Новые тесты =====

    @Test
    void returnsZerosWhenNoDemands() {
        Node root = node("root", 0, 0);
	Node a = node("a", 1, 0);
	Edge edge = edge("e1", a, root);

	Map<String, Double> result = new FlowPropagator().propagate(
	     root, Map.of(a, edge), Collections.emptyMap());

	// По логике FlowPropagator: если demands пустой, возвращаются все
	// участки из parentEdge с расходом 0.0. Это ожидаемо.
	assertEquals(1, result.size());
	assertEquals(0.0, result.get("e1"), 1e-9);
    }
    @Test
    void singleOksSingleSegment() {
        Node root = node("root", 0, 0);
        Node oks = node("oks", 1, 0);
        Edge edge = edge("e1", oks, root);

        Map<String, Double> result = new FlowPropagator().propagate(
                root, Map.of(oks, edge), Map.of(oks, 42.5));

        assertEquals(1, result.size());
        assertEquals(42.5, result.get("e1"), 1e-9);
    }

    @Test
    void threeOksOnSharedSegmentSumFlows() {
        Node root = node("root", 0, 0);
        Node junction = node("junction", 1, 0);
        Node a = node("a", 2, 1);
        Node b = node("b", 2, -1);
        Node c = node("c", 3, 0);

        Edge shared = edge("shared", junction, root);

        Map<String, Double> result = new FlowPropagator().propagate(root,
                Map.of(junction, shared,
                        a, edge("a", a, junction),
                        b, edge("b", b, junction),
                        c, edge("c", c, junction)),
                Map.of(a, 10.0, b, 20.0, c, 30.0));

        assertEquals(60.0, result.get("shared"), 1e-9);
        assertEquals(10.0, result.get("a"), 1e-9);
        assertEquals(20.0, result.get("b"), 1e-9);
        assertEquals(30.0, result.get("c"), 1e-9);
    }

    @Test
    void throwsWhenRootIsNull() {
        Node a = node("a", 1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(
                        null, Map.of(a, edge("e1", a, a)), Map.of(a, 1.0)));
    }

    @Test
    void throwsWhenParentEdgeIsNull() {
        Node root = node("root", 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(root, null, Collections.emptyMap()));
    }

    @Test
    void throwsWhenDemandsIsNull() {
        Node root = node("root", 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(root, Collections.emptyMap(), null));
    }

    @Test
    void throwsWhenOksHasNoPathToRoot() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);
        Node b = node("b", 2, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(root,
                        Map.of(a, edge("e1", a, b)),
                        Map.of(b, 5.0)));
    }

    @Test
    void throwsWhenFlowIsNegative() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);
        Edge edge = edge("e1", a, root);

        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(
                        root, Map.of(a, edge), Map.of(a, -5.0)));
    }

    @Test
    void throwsWhenFlowIsNaN() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);
        Edge edge = edge("e1", a, root);

        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(
                        root, Map.of(a, edge), Map.of(a, Double.NaN)));
    }

    @Test
    void throwsWhenFlowIsInfinite() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);
        Edge edge = edge("e1", a, root);

        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(
                        root, Map.of(a, edge), Map.of(a, Double.POSITIVE_INFINITY)));
    }

    @Test
    void throwsWhenEdgeIdIsDuplicated() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);
        Node b = node("b", 2, 0);

        Edge e1 = edge("dup", a, root);
        Edge e2 = edge("dup", b, root);

        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(root,
                        Map.of(a, e1, b, e2),
                        Map.of(a, 1.0, b, 2.0)));
    }

    @Test
    void throwsWhenEdgeOrientationIsWrong() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);

        Edge wrongEdge = edge("e1", root, a);

        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(root,
                        Map.of(a, wrongEdge),
                        Map.of(a, 1.0)));
    }

    @Test
    void parentEdgeForRootIsIgnored() {
        Node root = node("root", 0, 0);
        Node a = node("a", 1, 0);
        Node b = node("b", 2, 0);

        Edge rootEdge = edge("rootEdge", root, b);
        Edge aEdge = edge("a", a, root);

        Map<Node, Edge> parentEdge = new HashMap<>();
        parentEdge.put(a, aEdge);
        parentEdge.put(root, rootEdge);

        assertThrows(IllegalArgumentException.class,
                () -> new FlowPropagator().propagate(
                        root, parentEdge, Map.of(a, 1.0)));
    }
}
