package ru.lct.heatrouting.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExistingChamberCapacityIntegrationTest {
    @Test
    void allExistingChambersHaveAtMostFourIncidentPipes() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        InputDataset metric = new DatasetCoordinateTransformService(new CoordinateTransformService())
                .toMetric(new DatasetReader(new ObjectMapper(), new GeoJsonGeometryReader())
                        .read(Path.of(resource.toURI())));
        MultiOksRoutePreparationService.Preparation result =
                new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                        new ConnectionResolver(), new OksConnectionGrouper()).prepare(metric);
        int connected = result.getGroups().stream().mapToInt(java.util.List::size).sum();
        assertEquals(metric.getConnectionPoints().size(),
                connected + result.getUnconnectedOksIds().size());
        for (HeatChamber chamber : metric.getHeatChambers()) {
            int existing = 0;
            for (HeatNetworkSegment segment : metric.getHeatNetwork()) {
                if (segment.getGeometry().distance(chamber.getGeometry()) > 0.01) continue;
                boolean endpoint = segment.getGeometry().getStartPoint().distance(chamber.getGeometry()) <= 0.01
                        || segment.getGeometry().getEndPoint().distance(chamber.getGeometry()) <= 0.01;
                existing += endpoint ? 1 : 2;
            }
            String chamberId = chamber.getId();
            int added = result.getGroups().stream().mapToInt(group -> (int) group.stream()
                    .filter(route -> route.getConnection().isExistingChamber()
                            && chamberId.equals(route.getConnection().getChamberId())).count()).sum();
            System.out.println("Камера " + chamberId + ": существующих участков " + existing
                    + ", новых " + added + ", всего " + (existing + added));
            if (added > 0) assertTrue(existing + added <= 4, "Перегружена камера " + chamberId);
        }
        System.out.println("Подключено ОКС: " + connected + ", без маршрута: "
                + result.getUnconnectedOksIds());
    }
}
