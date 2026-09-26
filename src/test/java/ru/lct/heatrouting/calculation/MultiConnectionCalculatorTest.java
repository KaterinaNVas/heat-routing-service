package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiConnectionCalculatorTest {

    private final GeometryFactory factory = new GeometryFactory();

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

    @Test
    void sumsFlowsOnSharedSegmentAndPicksLargerDiameter() {
        Node root = node("root", 0, 0);
        Node junction = node("junction", 100, 0);
        Node a = node("a", 200, 0);
        Node b = node("b", 300, 0);

        Edge shared = edge("shared", junction, root, 100);
        Edge edgeA = edge("a", a, junction, 100);
        Edge edgeB = edge("b", b, junction, 100);

        MultiConnectionCalculator calc = new MultiConnectionCalculator(
                new FlowPropagator(), new VariantCalculator(0.7, 0.3));

        VariantSummary summary = calc.calculate(
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

        MultiConnectionCalculator calc = new MultiConnectionCalculator(
                new FlowPropagator(), new VariantCalculator(0.7, 0.3));

        // demand только для a -> orphan-ребро получит flow = 0
        VariantSummary summary = calc.calculate(
                "v1",
                root,
                Map.of(junction, shared, a, edgeA, orphan, edgeOrphan),
                Map.of(a, 5.0),
                false,
                List.of(),
                List.of());

        // Участок orphan имеет flow=0 и не должен попасть в segments и стоимость
        assertEquals(2, summary.getSegments().size());
        assertEquals(200.0, summary.getNewNetworkLength(), 1e-9);
        assertTrue(summary.getSegments().stream().noneMatch(s -> s.getId().equals("orphan")));
    }

    @Test
    void existingChamberAddsTieInCost() {
        Node root = node("root", 0, 0);
        Node a = node("a", 100, 0);
        Edge e = edge("a", a, root, 100);

        MultiConnectionCalculator calc = new MultiConnectionCalculator(
                new FlowPropagator(), new VariantCalculator(0.7, 0.3));

        VariantSummary summary = calc.calculate(
                "v1", root, Map.of(a, e), Map.of(a, 2.0),
                true, List.of(), List.of());

        assertEquals(0.0, summary.getChamberConstructionCost(), 1e-9);
        assertEquals(1, summary.getExistingChamberTieInCount());
        assertEquals(5_000_000.0, summary.getExistingChamberTieInCost(), 1e-9);
        assertEquals(summary.getConstructionCost(),
                summary.getCalculatedCost() - summary.getUnconnectedPenalty(), 1e-9);
    }

    @Test
    void unconnectedOksAddsPenalty() {
        Node root = node("root", 0, 0);
        Node a = node("a", 100, 0);
        Edge e = edge("a", a, root, 100);

        MultiConnectionCalculator calc = new MultiConnectionCalculator(
                new FlowPropagator(), new VariantCalculator(0.7, 0.3));

        VariantSummary summary = calc.calculate(
                "v1", root, Map.of(a, e), Map.of(a, 2.0),
                false, List.of("oks_99"), List.of(50.0));

        double expectedPenalty = 100_000_000 + 500_000 * 50.0;
        assertEquals(expectedPenalty, summary.getUnconnectedPenalty(), 1e-6);
        assertEquals(1, summary.getUnconnectedOksIds().size());
        assertEquals("oks_99", summary.getUnconnectedOksIds().get(0));
    }

    @Test
    void rejectsEmptyNetwork() {
        MultiConnectionCalculator calc = new MultiConnectionCalculator(
                new FlowPropagator(), new VariantCalculator(0.7, 0.3));
        assertThrows(IllegalArgumentException.class,
                () -> calc.calculate("v1", node("root", 0, 0),
                        Map.of(), Map.of(), false, List.of(), List.of()));
    }
}
