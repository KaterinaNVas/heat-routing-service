package ru.lct.heatrouting.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.geo.RouteAngleValidator;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.network.ConnectionResolver;
import ru.lct.heatrouting.network.MultiOksRoutePreparationService;
import ru.lct.heatrouting.network.OksConnectionGrouper;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryRoutePlannerAngleIntegrationTest {
    @Test
    void allPreparedRoutesHaveAllowedTurnsOnRealDataset() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        InputDataset metric = new DatasetCoordinateTransformService(new CoordinateTransformService())
                .toMetric(new DatasetReader(new ObjectMapper(), new GeoJsonGeometryReader())
                        .read(Path.of(resource.toURI())));
        var prepared = new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                new ConnectionResolver(), new OksConnectionGrouper()).prepare(metric);
        RouteAngleValidator validator = new RouteAngleValidator();
        int count = 0;
        for (var group : prepared.getGroups()) {
            for (var item : group) {
                count++;
                var route = item.getRoute().getGeometry();
                var check = validator.validate(route);
                assertTrue(check.isValid(), "ОКС " + item.getOks().getId() + ": " + check.getErrors());
            }
        }
        assertEquals(17, count);
        assertTrue(prepared.getUnconnectedOksIds().isEmpty());
    }
}
