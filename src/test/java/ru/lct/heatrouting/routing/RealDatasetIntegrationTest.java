package ru.lct.heatrouting.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealDatasetIntegrationTest {

    private DatasetReader datasetReader;
    private DatasetCoordinateTransformService transformService;
    private TerritoryRoutePlanner planner;

    @BeforeEach
    void setUp() {
        datasetReader = new DatasetReader(new ObjectMapper(), new GeoJsonGeometryReader());
        transformService = new DatasetCoordinateTransformService(new CoordinateTransformService());
        planner = new TerritoryRoutePlanner();
    }

    @Test
    void routesAllConnectionPointsOnRealDataset() throws Exception {
        URL url = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(url, "test-dataset.geojson не найден в classpath");
        InputDataset metric = transformService.toMetric(datasetReader.read(Path.of(url.toURI())));

        assertNotNull(metric.getSource(), "Источник не найден");
        assertTrue(!metric.getHeatNetwork().isEmpty(), "Нет участков heat_network");
        assertEquals(17, metric.getConnectionPoints().size(), "Ожидалось 17 ОКС");
        assertTrue(!metric.getRestrictions().isEmpty(), "Нет restrictions");

        List<String> notRoutedIds = new ArrayList<>();
        for (ConnectionPoint oks : metric.getConnectionPoints()) {
            TerritoryRoutePlanner.Route route = planner.find(metric, oks, 100).orElse(null);
            if (route == null) {
                notRoutedIds.add(oks.getId());
                continue;
            }
            assertTrue(route.getGeometry().getLength() > 0, "Пустой маршрут ОКС " + oks.getId());
            assertTrue(route.getGeometry().getStartPoint().distance(oks.getGeometry()) < 0.01,
                    "Неверное начало маршрута ОКС " + oks.getId());
            HeatNetworkSegment segment = metric.getHeatNetwork().stream()
                    .filter(s -> s.getId().equals(route.getExistingSegmentId()))
                    .findFirst().orElseThrow();
            assertTrue(segment.getGeometry().distance(route.getGeometry().getEndPoint()) < 0.01,
                    "Маршрут ОКС " + oks.getId() + " не доходит до сети");
        }
        assertTrue(notRoutedIds.isEmpty(), "Нет маршрута для ОКС " + notRoutedIds);
    }
}
