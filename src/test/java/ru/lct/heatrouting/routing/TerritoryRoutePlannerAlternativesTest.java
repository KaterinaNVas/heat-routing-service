package ru.lct.heatrouting.routing;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRoutePlannerAlternativesTest {
    private final GeometryFactory geometry = new GeometryFactory();

    @Test
    void findsDistinctTieInsAndKeepsConstructionOutsideOwnBuilding() {
        InputDataset dataset = new InputDataset();
        dataset.getHeatNetwork().add(segment("near", 100));
        dataset.getHeatNetwork().add(segment("far", 115));

        Polygon ownBuilding = geometry.createPolygon(new Coordinate[]{
                new Coordinate(-4, -4), new Coordinate(4, -4),
                new Coordinate(4, 4), new Coordinate(-4, 4),
                new Coordinate(-4, -4)});
        ownBuilding.setSRID(32637);
        dataset.getRestrictions().add(new Restriction("own", RestrictionType.OKS,
                null, ownBuilding));

        Point start = geometry.createPoint(new Coordinate(0, 0));
        start.setSRID(32637);
        ConnectionPoint oks = new ConnectionPoint("oks", 2.0, null, start);
        TerritoryRoutePlanner planner = new TerritoryRoutePlanner();

        List<TerritoryRoutePlanner.Route> alternatives =
                planner.findAlternatives(dataset, oks, 100, 3);

        assertEquals(2, alternatives.size());
        assertEquals("near", alternatives.get(0).getExistingSegmentId());
        assertEquals("far", alternatives.get(1).getExistingSegmentId());
        assertEquals(planner.find(dataset, oks, 100).orElseThrow().getExistingSegmentId(),
                alternatives.get(0).getExistingSegmentId());
        for (TerritoryRoutePlanner.Route route : alternatives) {
            assertEquals(0.0,
                    route.getConstructionGeometry().intersection(ownBuilding).getLength(), 0.01);
            assertTrue(route.getConstructionGeometry().getStartPoint().distance(start) > 0);
        }
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class,
                () -> new TerritoryRoutePlanner().findAlternatives(
                        new InputDataset(), new ConnectionPoint("oks", 2.0, null,
                                geometry.createPoint(new Coordinate(0, 0))), 100, 0));
    }

    private HeatNetworkSegment segment(String id, double x) {
        LineString line = geometry.createLineString(new Coordinate[]{
                new Coordinate(x, -10), new Coordinate(x, 10)});
        line.setSRID(32637);
        return new HeatNetworkSegment(id, 100, null, null, line);
    }
}
