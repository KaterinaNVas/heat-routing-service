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
        assertEquals(unit * (6 + 4 * 1.60), cost, 0.01);
    }

    @Test
    void overlappingCrossingsUseMaximumCoefficient() {
        double cost = calculator.calculate(segment, route, List.of(
                strip("road", RestrictionType.ROAD, 3, 7),
                strip("tram", RestrictionType.TRAM_TRACKS, 5, 9)));
        assertEquals(unit * (4 + 2 * 1.60 + 4 * 1.75), cost, 0.01);
    }

    @Test
    void withoutCrossingsUsesBasePrice() {
        assertEquals(unit * 10, calculator.calculate(segment, route, List.of()), 0.01);
    }
}
