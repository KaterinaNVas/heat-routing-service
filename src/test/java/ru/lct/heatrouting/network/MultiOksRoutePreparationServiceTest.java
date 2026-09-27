package ru.lct.heatrouting.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOksRoutePreparationServiceTest {
    private final GeometryFactory factory = new GeometryFactory();

    private Point point(double x, double y) {
        Point point = factory.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    @Test
    void preparesTwoOksAtOneExistingChamber() {
        InputDataset metric = new InputDataset();
        LineString pipe = factory.createLineString(new Coordinate[]{
                new Coordinate(100, -10), new Coordinate(100, 10)});
        pipe.setSRID(32637);
        metric.getHeatNetwork().add(new HeatNetworkSegment("pipe", 100, null, null, pipe));
        metric.getHeatChambers().add(new HeatChamber("chamber", 100, null, point(100, 0)));
        metric.getConnectionPoints().add(new ConnectionPoint("oks-1", 2.0, null, point(0, 0)));
        metric.getConnectionPoints().add(new ConnectionPoint("oks-2", 3.0, null, point(0, 5)));

        MultiOksRoutePreparationService service = new MultiOksRoutePreparationService(
                new TerritoryRoutePlanner(), new ConnectionResolver(), new OksConnectionGrouper());
        MultiOksRoutePreparationService.Preparation result = service.prepare(metric);

        assertTrue(result.getUnconnectedOksIds().isEmpty());
        assertEquals(1, result.getGroups().size());
        assertEquals(2, result.getGroups().get(0).size());
        for (MultiOksRoutePreparationService.PreparedConnection entry : result.getGroups().get(0)) {
            assertEquals("chamber", entry.getConnection().getChamberId());
            assertTrue(entry.getConnection().isExistingChamber());
            assertTrue(entry.getRoute().getGeometry().getEndPoint().distance(point(100, 0)) < 0.01);
        }
    }

    @Test
    void preparesAllOksFromRealDatasetExactlyOnce() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        ObjectMapper mapper = new ObjectMapper();
        InputDataset wgs84 = new DatasetReader(mapper, new GeoJsonGeometryReader())
                .read(Path.of(resource.toURI()));
        InputDataset metric = new DatasetCoordinateTransformService(
                new CoordinateTransformService()).toMetric(wgs84);

        MultiOksRoutePreparationService service = new MultiOksRoutePreparationService(
                new TerritoryRoutePlanner(), new ConnectionResolver(), new OksConnectionGrouper());
        MultiOksRoutePreparationService.Preparation result = service.prepare(metric);

        assertTrue(result.getUnconnectedOksIds().isEmpty());
        Set<String> oksIds = new HashSet<>();
        for (var group : result.getGroups()) {
            for (var entry : group) {
                assertTrue(oksIds.add(entry.getOks().getId()), "ОКС повторяется в группах");
                assertTrue(entry.getRoute().getGeometry().getEndPoint()
                        .distance(entry.getConnection().getPoint()) <= 0.01);
            }
        }
        assertEquals(17, oksIds.size());
    }
}
