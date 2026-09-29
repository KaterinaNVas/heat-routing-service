package ru.lct.heatrouting.routing;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.calculation.SpecialCrossingCostCalculator;
import ru.lct.heatrouting.network.NewSegment;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRoutePlannerTest {
    private final GeometryFactory gf = new GeometryFactory();

    private InputDataset dataset() {
        InputDataset data = new InputDataset();
        LineString existing = gf.createLineString(new Coordinate[]{
                new Coordinate(100, -10), new Coordinate(100, 10)});
        existing.setSRID(32637);
        data.getHeatNetwork().add(new HeatNetworkSegment("network", 100, null, null, existing));
        return data;
    }

    private ConnectionPoint oks(double x, double y) {
        org.locationtech.jts.geom.Point point = gf.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return new ConnectionPoint("oks", 2.0, null, point);
    }

    private Polygon square(double left, double right, double bottom, double top) {
        Polygon polygon = gf.createPolygon(new Coordinate[]{
                new Coordinate(left, bottom), new Coordinate(right, bottom),
                new Coordinate(right, top), new Coordinate(left, top),
                new Coordinate(left, bottom)});
        polygon.setSRID(32637);
        return polygon;
    }

    @Test
    void directRouteToExistingSegment() {
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(dataset(), oks(0, 0), 100).orElseThrow();
        assertEquals("network", route.getExistingSegmentId());
        assertEquals(100, route.getGeometry().getLength(), 1e-6);
    }

    @Test
    void detoursAroundProhibitedPolygon() {
        InputDataset data = dataset();
        Polygon obstacle = square(40, 60, -10, 10);
        data.getRestrictions().add(new Restriction("barrier", RestrictionType.PROHIBITED_SITE, null, obstacle));
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(data, oks(0, 0), 100).orElseThrow();
        assertTrue(route.getGeometry().getLength() > 100);
        assertTrue(route.getGeometry().distance(obstacle) >= 1.25);
    }

    @Test
    void allowsStraightExitFromOwnOksPolygon() {
        InputDataset data = dataset();
        data.getRestrictions().add(new Restriction("own", RestrictionType.OKS, null,
                square(-4, 4, -4, 4)));
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(data, oks(0, 0), 100).orElseThrow();
        assertEquals(0, route.getGeometry().getStartPoint().getX(), 1e-6);
        assertEquals(100, route.getGeometry().getEndPoint().getX(), 1e-6);
    }

    @Test
    void ignoresDistantRestrictionsOutsideLocalSearch() {
        InputDataset data = dataset();
        for (int i = 0; i < 100; i++) {
            data.getRestrictions().add(new Restriction("remote-" + i,
                    RestrictionType.PROHIBITED_SITE, null,
                    square(1000 + 20 * i, 1005 + 20 * i, -5, 5)));
        }
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner()
                .find(data, oks(0, 0), 100).orElseThrow();
        assertEquals(100, route.getGeometry().getLength(), 1e-6);
    }

    @Test
    void routesAroundDenseBoundaryWithoutCrossingIt() {
        InputDataset data = dataset();
        Polygon obstacle = (Polygon) gf.createPoint(new Coordinate(50, 0)).buffer(10, 512);
        obstacle.setSRID(32637);
        data.getRestrictions().add(new Restriction("dense", RestrictionType.PROHIBITED_SITE, null, obstacle));
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(data, oks(0, 0), 100).orElseThrow();
        assertTrue(route.getGeometry().getLength() > 100);
        assertTrue(route.getGeometry().distance(obstacle) >= 1.25);
    }

    @Test
    void straightSpecialExitFromExistingChamberInsideRoad() {
        InputDataset data = new InputDataset();
        LineString existing = gf.createLineString(new Coordinate[]{
                new Coordinate(95, -1), new Coordinate(95, 1)});
        existing.setSRID(32637);
        data.getHeatNetwork().add(new HeatNetworkSegment("inside", 100, null, null, existing));
        Polygon road = square(90, 100, -10, 10);
        Restriction restriction = new Restriction("road", RestrictionType.ROAD, null, road);
        data.getRestrictions().add(restriction);
        org.locationtech.jts.geom.Point chamber = gf.createPoint(new Coordinate(95, 0));
        chamber.setSRID(32637);
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner()
                .findTo(data, oks(0, 0), 100, chamber, "inside").orElseThrow();
        assertTrue(route.getGeometry().intersection(road).getLength() >= 5.0 - 1e-6);
        SpecialCrossingCostCalculator special = new SpecialCrossingCostCalculator();
        special.validateStraightSpecialPasses(route.getGeometry(), List.of(restriction));
        double pricedSpecialLength = special.parts(
                new NewSegment("new", 100, route.getGeometry().getLength()),
                route.getGeometry(), List.of(restriction)).stream()
                .filter(SpecialCrossingCostCalculator.Part::isSpecial)
                .mapToDouble(part -> part.getGeometry().getLength()).sum();
        assertEquals(8.0, pricedSpecialLength, 0.01);
    }
}
