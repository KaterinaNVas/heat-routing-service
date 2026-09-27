package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.network.ConnectionResolver;
import ru.lct.heatrouting.network.SingleConnectionDraftBuilder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiConnectionCalculatorTest {

    private final GeometryFactory factory = new GeometryFactory();

    /** Хелпер: создаёт калькулятор с правильными зависимостями. */
    private MultiConnectionCalculator newCalc() {
        return new MultiConnectionCalculator(
                new FlowPropagator(),
                new VariantCalculator(0.7, 0.3),
                new TopologyValidator());
    }

    private Node node(String id, double x, double y) {
        return new Node(id, factory.createPoint(new Coordinate(x, y)), "heat_chamber");
    }

    private Edge edge(String id, Node from, Node to, double lengthMeters) {
        Coordinate fromCoord = from.getPoint().getCoordinate();
        Coordinate toCoord = new Coordinate(fromCoord.x + lengthMeters, fromCoord.y);
        return new Edge(id, from, to,
                factory.createLineString(new Coordinate[]{fromCoord, toCoord}),
                0, 0, "base");
    }

    /** Хелпер: мокает Draft для тестов calculateVariant. */
    private SingleConnectionDraftBuilder.Draft mockDraft(Node root,
                                                         Map<Node, Edge> parentEdge,
                                                         Map<Node, Double> demands,
                                                         boolean existingChamber) {
        ConnectionResolver.Connection conn = mock(ConnectionResolver.Connection.class);
        when(conn.isExistingChamber()).thenReturn(existingChamber);

        SingleConnectionDraftBuilder.Draft draft =
                mock(SingleConnectionDraftBuilder.Draft.class);
        when(draft.getRoot()).thenReturn(root);
        when(draft.getParentEdge()).thenReturn(parentEdge);
        when(draft.getDemands()).thenReturn(demands);
        when(draft.getConnection()).thenReturn(conn);
        return draft;
    }

    // ===== Существующие тесты =====

    @Test
    void sumsFlowsOnSharedSegmentAndPicksLargerDiameter() {
        Node root = node("root", 0, 0);
        Node junction = node("junction", 100, 0);
        Node a = node("a", 200, 0);
        Node b = node("b", 300, 0);

        Edge shared = edge("shared", junction, root, 100);
        Edge edgeA = edge("a", a, junction, 100);
        Edge edgeB = edge("b", b, junction, 100);

        VariantSummary summary = newCalc().calculate(
                "v1",
                root,
                Map.of(junction, shared, a, edgeA, b, edgeB),
                Map.of(a, 3.5, b, 8.0),
                false,
                List.of(),
                List.of());

        assertEquals(3, summary.getSegments().size());
        assertEquals(11.5, summary.getFlowsByEdgeId().get("shared"), 1e-9);
        assertEquals(300.0, summary.getNewNetworkLength(), 1e-9);
        assertEquals(3_000_000, summary.getChamberConstructionCost(), 1e-9);
        assertEquals(0, summary.getExistingChamberTieInCost(), 1e-9);
        assertEquals(0, summary.getUnconnectedPenalty(), 1e-9);
        assertTrue(summary.getCalculatedCost() > 0);
        assertTrue(summary.getScore() > 0);
        assertEquals(1, summary.getRank());
    }

    @Test
    void zeroFlowEdgeIsNotIncludedInSegmentsAndCost() {
        Node root = node("root", 0, 0);
        Node junction = node("junction", 100, 0);
        Node a = node("a", 200, 0);
        Node orphan = node("orphan", 300, 0);

        Edge shared = edge("shared", junction, root, 100);
        Edge edgeA = edge("a", a, junction, 100);
        Edge edgeOrphan = edge("orphan", orphan, junction, 100);

        VariantSummary summary = newCalc().calculate(
                "v1",
                root,
                Map.of(junction, shared, a, edgeA, orphan, edgeOrphan),
                Map.of(a, 5.0),
                false,
                List.of(),
                List.of());

        assertEquals(2, summary.getSegments().size());
        assertEquals(200.0, summary.getNewNetworkLength(), 1e-9);
        assertTrue(summary.getSegments().stream()
                .noneMatch(s -> s.getId().equals("orphan")));
    }

    @Test
    void existingChamberAddsTieInCost() {
        Node root = node("root", 0, 0);
        Node a = node("a", 100, 0);
        Edge e = edge("a", a, root, 100);

        VariantSummary summary = newCalc().calculate(
                "v1", root, Map.of(a, e), Map.of(a, 2.0),
                true, List.of(), List.of());

        assertEquals(0.0, summary.getChamberConstructionCost(), 1e-9);
        assertEquals(1, summary.getExistingChamberTieInCount());
        assertEquals(5_000_000.0, summary.getExistingChamberTieInCost(), 1e-9);
    }

    @Test
    void unconnectedOksAddsPenalty() {
        Node root = node("root", 0, 0);
        Node a = node("a", 100, 0);
        Edge e = edge("a", a, root, 100);

        VariantSummary summary = newCalc().calculate(
                "v1", root, Map.of(a, e), Map.of(a, 2.0),
                false, List.of("oks_99"), List.of(50.0));

        double expectedPenalty = 100_000_000 + 500_000 * 50.0;
        assertEquals(expectedPenalty, summary.getUnconnectedPenalty(), 1e-6);
        assertEquals(1, summary.getUnconnectedOksIds().size());
        assertEquals("oks_99", summary.getUnconnectedOksIds().get(0));
    }

    @Test
    void rejectsEmptyNetwork() {
        Node root = node("root", 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> newCalc().calculate("v1", root,
                        Map.of(), Map.of(), false, List.of(), List.of()));
    }

    @Test
    void threeOksOnSharedSegmentSumFlows() {
        Node root = node("root", 0, 0);
        Node junction = node("junction", 100, 0);
        Node a = node("a", 200, 0);
        Node b = node("b", 300, 0);
        Node c = node("c", 400, 0);

        Edge shared = edge("shared", junction, root, 100);
        Edge eA = edge("a", a, junction, 100);
        Edge eB = edge("b", b, junction, 100);
        Edge eC = edge("c", c, junction, 100);

        VariantSummary summary = newCalc().calculate(
                "v1", root,
                Map.of(junction, shared, a, eA, b, eB, c, eC),
                Map.of(a, 10.0, b, 20.0, c, 30.0),
                false, List.of(), List.of());

        assertEquals(4, summary.getSegments().size());
        assertEquals(60.0, summary.getFlowsByEdgeId().get("shared"), 1e-9);
        assertEquals(10.0, summary.getFlowsByEdgeId().get("a"), 1e-9);
        assertEquals(20.0, summary.getFlowsByEdgeId().get("b"), 1e-9);
        assertEquals(30.0, summary.getFlowsByEdgeId().get("c"), 1e-9);
    }

    // ===== Новые тесты: calculateVariant =====

    @Test
    void calculateVariantForSingleTreeMatchesCalculate() {
        Node root = node("root", 0, 0);
        Node oks = node("oks", 100, 0);
        Edge e = edge("e1", oks, root, 100);

        SingleConnectionDraftBuilder.Draft draft = mockDraft(
                root, Map.of(oks, e), Map.of(oks, 5.0), false);

        VariantSummary summary = newCalc().calculateVariant(
                "v1", List.of(draft), List.of(), List.of());

        assertEquals(1, summary.getSegments().size());
        assertTrue(summary.getCalculatedCost() > 0);
        assertEquals(1, summary.getRank());
    }

    @Test
    void calculateVariantSumsCostsAndSegmentsOfTwoTrees() {
        // Дерево 1
        Node root1 = node("root1", 0, 0);
        Node oks1 = node("oks1", 100, 0);
        Edge e1 = edge("e1", oks1, root1, 100);
        SingleConnectionDraftBuilder.Draft draft1 = mockDraft(
                root1, Map.of(oks1, e1), Map.of(oks1, 5.0), false);

        // Дерево 2
        Node root2 = node("root2", 0, 500);
        Node oks2 = node("oks2", 100, 500);
        Edge e2 = edge("e2", oks2, root2, 100);
        SingleConnectionDraftBuilder.Draft draft2 = mockDraft(
                root2, Map.of(oks2, e2), Map.of(oks2, 10.0), false);

        VariantSummary summary = newCalc().calculateVariant(
                "v1", List.of(draft1, draft2), List.of(), List.of());
        System.out.println(">>> AFTER calculateVariant: " + summary);
	System.out.println(">>> segments size = " + summary.getSegments().size());
	System.out.println(">>> segments = " + summary.getSegments());
        assertEquals(2, summary.getSegments().size());
	assertEquals(200.0, summary.getNewNetworkLength(), 1e-9);
	assertEquals(0, summary.getExistingChamberTieInCount());   // ← 0, потому что камеры новые
	assertTrue(summary.getChamberConstructionCost() > 0);       // ← новые камеры → стоимость > 0
	assertTrue(summary.getCalculatedCost() > 0);
    }

    @Test
    void calculateVariantForThreeTrees() {
        Node root1 = node("root1", 0, 0);
        Node oks1 = node("oks1", 100, 0);
        Edge e1 = edge("e1", oks1, root1, 100);

        Node root2 = node("root2", 0, 500);
        Node oks2 = node("oks2", 100, 500);
        Edge e2 = edge("e2", oks2, root2, 100);

        Node root3 = node("root3", 0, 1000);
        Node oks3 = node("oks3", 100, 1000);
        Edge e3 = edge("e3", oks3, root3, 100);

        VariantSummary summary = newCalc().calculateVariant(
                "v1",
                List.of(
                        mockDraft(root1, Map.of(oks1, e1), Map.of(oks1, 5.0), false),
                        mockDraft(root2, Map.of(oks2, e2), Map.of(oks2, 10.0), false),
                        mockDraft(root3, Map.of(oks3, e3), Map.of(oks3, 15.0), false)),
                List.of(),
                List.of());

        assertEquals(3, summary.getSegments().size());
        assertEquals(300.0, summary.getNewNetworkLength(), 1e-9);
    }

    @Test
    void calculateVariantAppliesPenaltyOnce() {
        Node root = node("root", 0, 0);
        Node oks = node("oks", 100, 0);
        Edge e = edge("e1", oks, root, 100);
        SingleConnectionDraftBuilder.Draft draft = mockDraft(
                root, Map.of(oks, e), Map.of(oks, 5.0), false);

        VariantSummary summary = newCalc().calculateVariant(
                "v1",
                List.of(draft),
                List.of("oks_99"),
                List.of(50.0));

        double expectedPenalty = 100_000_000 + 500_000 * 50.0;
        assertEquals(expectedPenalty, summary.getUnconnectedPenalty(), 1e-6);
        assertEquals(1, summary.getUnconnectedOksIds().size());
    }

    @Test
    void calculateVariantRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> newCalc().calculateVariant(
                        "v1", List.of(), List.of(), List.of()));
    }

    @Test
    void calculateVariantRejectsNullList() {
        assertThrows(NullPointerException.class,
                () -> newCalc().calculateVariant(
                        "v1", null, List.of(), List.of()));
    }

    @Test
    void calculateVariantCombinesSegmentsFromAllTrees() {
        Node root1 = node("root1", 0, 0);
        Node oks1 = node("oks1", 100, 0);
        Edge e1 = edge("e1", oks1, root1, 100);

        Node root2 = node("root2", 0, 500);
        Node oks2 = node("oks2", 100, 500);
        Edge e2 = edge("e2", oks2, root2, 100);

        VariantSummary summary = newCalc().calculateVariant(
                "v1",
                List.of(
                        mockDraft(root1, Map.of(oks1, e1), Map.of(oks1, 5.0), false),
                        mockDraft(root2, Map.of(oks2, e2), Map.of(oks2, 10.0), false)),
                List.of(),
                List.of());

        assertTrue(summary.getSegments().stream()
                .anyMatch(s -> s.getId().equals("e1")));
        assertTrue(summary.getSegments().stream()
                .anyMatch(s -> s.getId().equals("e2")));
    }
}
