package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.calculation.FlowPropagator;
import ru.lct.heatrouting.calculation.SingleConnectionCalculator;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.network.ConnectionResolver;
import ru.lct.heatrouting.network.SingleConnectionDraftBuilder;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleOksPreviewServiceTest {
    @Test
    void returnsCalculatedGeoJsonForOneOks() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CoordinateTransformService coordinates = new CoordinateTransformService();
        SingleOksPreviewService service = new SingleOksPreviewService(mapper,
                new DatasetReader(mapper, new GeoJsonGeometryReader()),
                new DatasetCoordinateTransformService(coordinates), coordinates,
                new TerritoryRoutePlanner(), new ConnectionResolver(),
                new SingleConnectionDraftBuilder(),
                new SingleConnectionCalculator(new FlowPropagator(), new VariantCalculator(0.7, 0.3)));
        String input = "{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"properties\":{\"id\":\"old\",\"object_type\":\"heat_network\",\"diameter\":100},"
                + "\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[37.601,55.7495],[37.601,55.7505]]}},"
                + "{\"type\":\"Feature\",\"properties\":{\"id\":7,\"object_type\":\"oks_connection_point\",\"flow_tph\":2},"
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[37.6,55.75]}}]}";
        Path file = Files.createTempFile("single-oks-test-", ".geojson");
        try {
            Files.write(file, input.getBytes(StandardCharsets.UTF_8));
            ObjectNode result = service.calculate(file, "7");
            assertEquals("FeatureCollection", result.path("type").asText());
            assertEquals(3, result.path("features").size());
            assertEquals("heat_network", result.path("features").get(0)
                    .path("properties").path("object_type").asText());
            assertTrue(result.path("features").get(0).path("properties")
                    .path("start_node_id").isNumber());
            assertEquals(50, result.path("features").get(0).path("properties")
                    .path("diameter").asInt());
            assertTrue(result.path("features").get(0).path("properties")
                    .path("length").asDouble() > 0);
            assertEquals("variant_summary", result.path("features").get(2)
                    .path("properties").path("object_type").asText());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
