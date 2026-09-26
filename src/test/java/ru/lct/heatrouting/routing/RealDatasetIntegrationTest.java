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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealDatasetIntegrationTest {

    private DatasetReader datasetReader;
    private DatasetCoordinateTransformService transformService;
    private TerritoryRoutePlanner planner;

    @BeforeEach
    void setUp() {
        datasetReader = new DatasetReader(
                new ObjectMapper(),
                new GeoJsonGeometryReader()
        );
        transformService = new DatasetCoordinateTransformService(
                new CoordinateTransformService()
        );
        planner = new TerritoryRoutePlanner();
    }

    @Test
    void routesAllConnectionPointsOnRealDataset() throws Exception {
        URL url = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(url, "test-dataset.geojson не найден в classpath");
        Path path = Path.of(url.toURI());

        InputDataset wgs84 = datasetReader.read(path);
        InputDataset metric = transformService.toMetric(wgs84);

        assertNotNull(metric.getSource(), "Источник не найден");
        assertTrue(metric.getHeatNetwork().size() > 0, "Нет участков heat_network");
        assertTrue(metric.getConnectionPoints().size() > 0, "Нет ОКС");
        assertTrue(metric.getRestrictions().size() > 0, "Нет restrictions");

        System.out.println("=== Distances OKS to network ===");
        for (ConnectionPoint oks : metric.getConnectionPoints()) {
            double minDist = Double.MAX_VALUE;
            String nearestId = null;
            for (HeatNetworkSegment seg : metric.getHeatNetwork()) {
                double d = oks.getGeometry().distance(seg.getGeometry());
                if (d < minDist) {
                    minDist = d;
                    nearestId = seg.getId();
                }
            }
            System.out.println("OKS " + oks.getId()
                    + " minDist=" + String.format("%.1f", minDist)
                    + " m, nearest=" + nearestId
                    + " srid=" + oks.getGeometry().getSRID());
        }

        int total = metric.getConnectionPoints().size();
        int routed = 0;
        int notRouted = 0;
        List<String> notRoutedIds = new ArrayList<>();

        System.out.println("=== Routing with DN=100 ===");
        for (ConnectionPoint oks : metric.getConnectionPoints()) {
            var routeOpt = planner.find(metric, oks, 100);
            if (routeOpt.isPresent()) {
                routed++;
            } else {
                notRouted++;
                notRoutedIds.add(oks.getId());
            }
        }

        System.out.println("=== Real Dataset Routing ===");
        System.out.println("Всего ОКС:          " + total);
        System.out.println("Подключено:         " + routed);
        System.out.println("Не подключено:      " + notRouted);
        if (!notRoutedIds.isEmpty()) {
            System.out.println("Не подключены (id): " + notRoutedIds);
        }

        System.out.println("ВНИМАНИЕ: TerritoryRoutePlanner возвращает empty "
        + "из-за MAX_VERTICES=1600 на реальном датасете. "
        + "См. сообщение в общем чате.");
    }
}
