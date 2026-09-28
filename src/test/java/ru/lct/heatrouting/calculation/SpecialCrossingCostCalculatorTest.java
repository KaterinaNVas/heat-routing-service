package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.cost.SegmentCostCalculator;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.network.NewSegment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpecialCrossingCostCalculatorTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final LineString route = factory.createLineString(new Coordinate[]{
            new Coordinate(0, 0), new Coordinate(10, 0)});
    private final NewSegment segment = new NewSegment("edge", 100, 10);
    private final SpecialCrossingCostCalculator calculator = new SpecialCrossingCostCalculator();
    private final double unit = new SegmentCostCalculator(new DiameterCatalog())
            .calculateNewSegmentCost(new NewSegment("unit", 100, 1));

    private Restriction strip(String id, RestrictionType type, double from, double to) {
        Polygon polygon = factory.createPolygon(new Coordinate[]{
                new Coordinate(from, -1), new Coordinate(to, -1),
                new Coordinate(to, 1), new Coordinate(from, 1),
                new Coordinate(from, -1)});
        return new Restriction(id, type, null, polygon);
    }

    @Test
    void increasesCostOnlyWithinCrossing() {
        double cost = calculator.calculate(segment, route,
                List.of(strip("road", RestrictionType.ROAD, 3, 7)));
        assertEquals(unit * 10 * 1.60, cost, 0.01);
    }

    @Test
    void overlappingCrossingsUseMaximumCoefficient() {
        double cost = calculator.calculate(segment, route, List.of(
                strip("road", RestrictionType.ROAD, 3, 7),
                strip("tram", RestrictionType.TRAM_TRACKS, 5, 9)));
        assertEquals(unit * (2 * 1.60 + 8 * 1.75), cost, 0.01);
    }

    @Test
    void withoutCrossingsUsesBasePrice() {
        assertEquals(unit * 10, calculator.calculate(segment, route, List.of()), 0.01);
    }

    @Test
    void returnsThreePiecesWithEndpointsAndIndividualCosts() {
        LineString longRoute = factory.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(20, 0)});
        List<SpecialCrossingCostCalculator.Part> pieces = calculator.parts(
                new NewSegment("long", 100, 20), longRoute,
                List.of(strip("road", RestrictionType.ROAD, 6, 10)));
        assertEquals(3, pieces.size());
        assertEquals(false, pieces.get(0).isSpecial());
        assertEquals(true, pieces.get(1).isSpecial());
        assertEquals(false, pieces.get(2).isSpecial());
        assertEquals(3, pieces.get(0).getGeometry().getLength(), 1e-7);
        assertEquals(10, pieces.get(1).getGeometry().getLength(), 1e-7);
        assertEquals(7, pieces.get(2).getGeometry().getLength(), 1e-7);
        assertEquals(3, pieces.get(0).getGeometry().getEndPoint().getX(), 1e-7);
        assertEquals(13, pieces.get(1).getGeometry().getEndPoint().getX(), 1e-7);
        assertEquals(unit * 3, pieces.get(0).getCost(), 0.01);
        assertEquals(unit * 10 * 1.60, pieces.get(1).getCost(), 0.01);
        assertEquals(unit * 7, pieces.get(2).getCost(), 0.01);
    }

    @Test
    void linearUtilityCrossingExtendsTwoMetersEachSide() {
        LineString longRoute = factory.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(20, 0)});
        LineString gas = factory.createLineString(new Coordinate[]{
                new Coordinate(10, -2), new Coordinate(10, 2)});
        List<SpecialCrossingCostCalculator.Part> pieces = calculator.parts(
                new NewSegment("gas", 100, 20), longRoute,
                List.of(new Restriction("gas", RestrictionType.GAS_PIPELINE, null, gas)));
        assertEquals(3, pieces.size());
        assertEquals(8, pieces.get(0).getGeometry().getLength(), 1e-7);
        assertEquals(4, pieces.get(1).getGeometry().getLength(), 1e-7);
        assertEquals(8, pieces.get(2).getGeometry().getLength(), 1e-7);
        assertEquals(unit * (16 + 4 * 1.25), calculator.calculate(
                new NewSegment("gas", 100, 20), longRoute,
                List.of(new Restriction("gas", RestrictionType.GAS_PIPELINE, null, gas))), 0.01);
    }
}
