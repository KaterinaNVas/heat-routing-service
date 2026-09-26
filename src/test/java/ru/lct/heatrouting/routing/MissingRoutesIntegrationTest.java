package ru.lct.heatrouting.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingRoutesIntegrationTest {
    @Test
    void routesPreviouslyMissingOksOnTestDataset() throws Exception {
        Path path = Path.of(Objects.requireNonNull(getClass().getClassLoader()
                .getResource("test-dataset.geojson")).toURI());
        InputDataset dataset = new DatasetCoordinateTransformService(
                new CoordinateTransformService()).toMetric(
                new DatasetReader(new ObjectMapper(), new GeoJsonGeometryReader()).read(path));
        TerritoryRoutePlanner planner = new TerritoryRoutePlanner();
        for (String id : new String[]{"2", "3", "5", "10"}) {
            ConnectionPoint oks = dataset.getConnectionPoints().stream()
                    .filter(p -> id.equals(p.getId())).findFirst().orElseThrow();
            TerritoryRoutePlanner.Route route = planner.find(dataset, oks, 100)
                    .orElseThrow(() -> new AssertionError("Нет маршрута для ОКС " + id));
            HeatNetworkSegment segment = dataset.getHeatNetwork().stream()
                    .filter(s -> route.getExistingSegmentId().equals(s.getId()))
                    .findFirst().orElseThrow();
            assertTrue(route.getGeometry().getLength() > 0, id);
            assertTrue(route.getGeometry().getStartPoint().distance(oks.getGeometry()) < 0.01, id);
            assertTrue(route.getGeometry().getEndPoint().distance(segment.getGeometry()) < 0.01, id);
        }
    }
}
