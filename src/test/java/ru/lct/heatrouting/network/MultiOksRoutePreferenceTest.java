package ru.lct.heatrouting.network;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOksRoutePreferenceTest {
    private final GeometryFactory geometry = new GeometryFactory();

    @Test
    void choosesNewChamberWhenItCostsLessThanNearExistingTieIn() {
        InputDataset dataset = new InputDataset();
        dataset.getHeatNetwork().add(segment("near", 100, -10, 10));
        dataset.getHeatNetwork().add(segment("far", 102, 20, 40));
        dataset.getHeatChambers().add(new HeatChamber("existing", 100, null,
                point(100, 0)));
        dataset.getConnectionPoints().add(new ConnectionPoint("oks", 2.0, null,
                point(0, 0)));

        MultiOksRoutePreparationService service = new MultiOksRoutePreparationService(
                new TerritoryRoutePlanner(), new ConnectionResolver(),
                new OksConnectionGrouper());

        MultiOksRoutePreparationService.PreparedConnection shortest = service.prepare(dataset)
                .getGroups().get(0).get(0);
        MultiOksRoutePreparationService.PreparedConnection cheaper = service.prepare(dataset,
                MultiOksRoutePreparationService.RoutePreference.LOWEST_STANDALONE_COST)
                .getGroups().get(0).get(0);

        assertTrue(shortest.getConnection().isExistingChamber());
        assertEquals("near", shortest.getConnection().getExistingSegmentId());
        assertFalse(cheaper.getConnection().isExistingChamber());
        assertEquals("far", cheaper.getConnection().getExistingSegmentId());
        assertTrue(cheaper.getRoute().getConstructionGeometry().getLength()
                > shortest.getRoute().getConstructionGeometry().getLength());
    }

    @Test
    void ignoresCandidateWhoseNearbyChamberIsOnAnotherSegment() {
        InputDataset dataset = new InputDataset();
        dataset.getHeatNetwork().add(segment("near", 100, -10, 10));
        dataset.getHeatNetwork().add(segment("other", 104, -10, 10));
        dataset.getHeatChambers().add(new HeatChamber("existing", 100, null,
                point(100, 0)));
        dataset.getConnectionPoints().add(new ConnectionPoint("oks", 2.0, null,
                point(0, 0)));

        MultiOksRoutePreparationService service = new MultiOksRoutePreparationService(
                new TerritoryRoutePlanner(), new ConnectionResolver(),
                new OksConnectionGrouper());

        MultiOksRoutePreparationService.Preparation result = service.prepare(dataset,
                MultiOksRoutePreparationService.RoutePreference.LOWEST_STANDALONE_COST);
        assertTrue(result.getUnconnectedOksIds().isEmpty());
        assertEquals("near", result.getGroups().get(0).get(0)
                .getConnection().getExistingSegmentId());
    }

    private Point point(double x, double y) {
        Point p = geometry.createPoint(new Coordinate(x, y));
        p.setSRID(32637);
        return p;
    }

    private HeatNetworkSegment segment(String id, double x, double bottom, double top) {
        LineString line = geometry.createLineString(new Coordinate[]{
                new Coordinate(x, bottom), new Coordinate(x, top)});
        line.setSRID(32637);
        return new HeatNetworkSegment(id, 100, null, null, line);
    }
}
