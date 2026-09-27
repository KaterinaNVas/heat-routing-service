package ru.lct.heatrouting.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.calculation.SpecialCrossingProcessor;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealDatasetCrossingAnglesTest {
    @Test
    void allPreparedRoutesMeetSpecialCrossingAngles() throws Exception {
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        ObjectMapper mapper = new ObjectMapper();
        InputDataset metric = new DatasetCoordinateTransformService(
                new CoordinateTransformService()).toMetric(
                new DatasetReader(mapper, new GeoJsonGeometryReader())
                        .read(Path.of(resource.toURI())));
        MultiOksRoutePreparationService.Preparation prepared =
                new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                        new ConnectionResolver(), new OksConnectionGrouper()).prepare(metric);
        SpecialCrossingProcessor crossings = new SpecialCrossingProcessor();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (List<MultiOksRoutePreparationService.PreparedConnection> group : prepared.getGroups()) {
            for (MultiOksRoutePreparationService.PreparedConnection connection : group) {
                checked++;
                try {
                    crossings.validateAngles(connection.getRoute().getGeometry(),
                            metric.getRestrictions());
                } catch (IllegalStateException exception) {
                    failures.add("ОКС " + connection.getOks().getId() + ": " + exception.getMessage());
                }
            }
        }
        assertEquals(17, checked);
        assertTrue(failures.isEmpty(), "Недопустимые пересечения: " + failures);
    }
}
