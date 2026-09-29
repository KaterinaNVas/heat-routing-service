package ru.lct.heatrouting.network;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.calculation.FlowPropagator;
import ru.lct.heatrouting.calculation.MultiConnectionCalculator;
import ru.lct.heatrouting.calculation.MultiTreeVariantCalculator;
import ru.lct.heatrouting.calculation.SpecialCrossingCostCalculator;
import ru.lct.heatrouting.calculation.TopologyValidator;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.calculation.VariantSummary;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.cost.SegmentCostCalculator;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiConnectionDraftBuilderTest {
    private final GeometryFactory factory = new GeometryFactory();

    private Point point(double x, double y) {
        Point p = factory.createPoint(new Coordinate(x, y));
        p.setSRID(32637);
        return p;
    }

    private InputDataset dataset(boolean sameOksPoint) {
        InputDataset metric = new InputDataset();
        LineString pipe = factory.createLineString(new Coordinate[]{
                new Coordinate(100, -10), new Coordinate(100, 10)});
        pipe.setSRID(32637);
        metric.getHeatNetwork().add(new HeatNetworkSegment("pipe", 100, null, null, pipe));
        metric.getHeatChambers().add(new HeatChamber("chamber", 100, null, point(100, 0)));
        metric.getConnectionPoints().add(new ConnectionPoint("oks-1", 2.0, null, point(0, 0)));
        metric.getConnectionPoints().add(new ConnectionPoint("oks-2", 3.0, null,
                sameOksPoint ? point(0, 0) : point(0, 5)));
        return metric;
    }

    private MultiOksRoutePreparationService.Preparation prepare(InputDataset metric) {
        return new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                new ConnectionResolver(), new OksConnectionGrouper()).prepare(metric);
    }

    @Test
    void twoIndependentPipesAtOneChamberAreChargedForTwoTieIns() {
        List<MultiConnectionDraftBuilder.Draft> drafts =
                new MultiConnectionDraftBuilder().build(prepare(dataset(false)));
        assertEquals(1, drafts.size());
        assertEquals(2, drafts.get(0).getParentEdge().size());

        VariantCalculator pricing = new VariantCalculator(0.7, 0.3);
        MultiTreeVariantCalculator totals = new MultiTreeVariantCalculator(
                new MultiConnectionCalculator(new FlowPropagator(), pricing,
                        new TopologyValidator()), pricing);
        VariantSummary summary = totals.calculateVariant("v1", drafts, List.of(), List.of());
        assertEquals(2, summary.getExistingChamberTieInCount());
        assertEquals(10_000_000, summary.getExistingChamberTieInCost(), 0.01);
        assertEquals(2, summary.getSegments().size());
        assertEquals(2.0, summary.getFlowsByEdgeId().get("multi_g1_e1"), 1e-9);
        assertEquals(3.0, summary.getFlowsByEdgeId().get("multi_g1_e2"), 1e-9);
        assertEquals(summary.getConstructionCost(), summary.getCalculatedCost(), 0.01);
    }

    @Test
    void identicalRoutesRequireOneSharedEdge() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new MultiConnectionDraftBuilder().build(prepare(dataset(true))));
        assertTrue(error.getMessage().contains("общий участок"));
    }

    @Test
    void specialCrossingRaisesTotalOnlyForCrossedLength() {
        List<MultiConnectionDraftBuilder.Draft> drafts =
                new MultiConnectionDraftBuilder().build(prepare(dataset(false)));
        VariantCalculator pricing = new VariantCalculator(0.3, 0.7);
        MultiTreeVariantCalculator totals = new MultiTreeVariantCalculator(
                new MultiConnectionCalculator(new FlowPropagator(), pricing,
                        new TopologyValidator()), pricing);
        VariantSummary base = totals.calculateVariant("v1", drafts, List.of(), List.of());
        Polygon road = factory.createPolygon(new Coordinate[]{
                new Coordinate(20, -10), new Coordinate(30, -10),
                new Coordinate(30, 10), new Coordinate(20, 10),
                new Coordinate(20, -10)});
        List<Restriction> restrictions = List.of(
                new Restriction("road", RestrictionType.ROAD, null, road));
        VariantSummary withRoad = totals.calculateVariant("v1", drafts, List.of(),
                List.of(), restrictions);
        SegmentCostCalculator prices = new SegmentCostCalculator(new DiameterCatalog());
        SpecialCrossingCostCalculator special = new SpecialCrossingCostCalculator();
        double expectedExtra = 0;
        for (var segment : base.getSegments()) {
            Edge edge = drafts.get(0).getParentEdge().values().stream()
                    .filter(e -> e.getId().equals(segment.getId())).findFirst().orElseThrow();
            expectedExtra += special.calculate(segment, edge.getGeometry(), restrictions)
                    - prices.calculateNewSegmentCost(segment);
        }
        assertTrue(expectedExtra > 0);
        assertEquals(base.getConstructionCost() + expectedExtra,
                withRoad.getConstructionCost(), 0.01);
    }
}
