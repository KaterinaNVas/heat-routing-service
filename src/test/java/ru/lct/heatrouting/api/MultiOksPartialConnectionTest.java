package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.calculation.FlowPropagator;
import ru.lct.heatrouting.calculation.MultiConnectionCalculator;
import ru.lct.heatrouting.calculation.MultiTreeVariantCalculator;
import ru.lct.heatrouting.calculation.TopologyValidator;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.network.ConnectionResolver;
import ru.lct.heatrouting.network.MultiConnectionDraftBuilder;
import ru.lct.heatrouting.network.MultiOksRoutePreparationService;
import ru.lct.heatrouting.network.OksConnectionGrouper;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOksPartialConnectionTest {
    @Test
    void reportsOneUnconnectedOksAndKeepsSuccessfulRoutes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        ObjectNode input = (ObjectNode) mapper.readTree(resource);
        ArrayNode features = (ArrayNode) input.path("features");

        ObjectNode oks = features.addObject();
        oks.put("type", "Feature");
        ObjectNode oksProperties = oks.putObject("properties");
        oksProperties.put("id", 9999);
        oksProperties.put("object_type", "oks_connection_point");
        oksProperties.put("flow_tph", 12.0);
        ObjectNode oksGeometry = oks.putObject("geometry");
        oksGeometry.put("type", "Point");
        oksGeometry.putArray("coordinates").add(37.65).add(55.72);

        // A small prohibited polygon completely surrounds only the added OKS.
        ObjectNode water = features.addObject();
        water.put("type", "Feature");
        ObjectNode waterProperties = water.putObject("properties");
        waterProperties.put("id", "isolated-water");
        waterProperties.put("object_type", "restriction");
        waterProperties.put("restriction_type", "WATER");
        ObjectNode waterGeometry = water.putObject("geometry");
        waterGeometry.put("type", "Polygon");
        ArrayNode ring = waterGeometry.putArray("coordinates").addArray();
        addPoint(ring, 37.6499, 55.7199);
        addPoint(ring, 37.6501, 55.7199);
        addPoint(ring, 37.6501, 55.7201);
        addPoint(ring, 37.6499, 55.7201);
        addPoint(ring, 37.6499, 55.7199);

        Path temporary = Files.createTempFile("partial-oks-", ".geojson");
        try {
            mapper.writeValue(temporary.toFile(), input);
            CoordinateTransformService coordinates = new CoordinateTransformService();
            VariantCalculator costs = new VariantCalculator(0.3, 0.7);
            MultiOksPreviewService service = new MultiOksPreviewService(mapper,
                    new DatasetReader(mapper, new GeoJsonGeometryReader()),
                    new DatasetCoordinateTransformService(coordinates), coordinates,
                    new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                            new ConnectionResolver(), new OksConnectionGrouper()),
                    new MultiConnectionDraftBuilder(),
                    new MultiTreeVariantCalculator(new MultiConnectionCalculator(
                            new FlowPropagator(), costs, new TopologyValidator()), costs), costs);

            JsonNode output = service.calculate(temporary);
            int routes = 0;
            JsonNode summary = null;
            for (JsonNode feature : output.path("features")) {
                String type = feature.path("properties").path("object_type").asText();
                if ("heat_network".equals(type)) routes++;
                if ("variant_summary".equals(type)) summary = feature.path("properties");
            }
            assertNotNull(summary);
            assertEquals(17, routes);
            assertEquals(1, summary.path("unconnected_oks_ids").size());
            assertEquals(9999, summary.path("unconnected_oks_ids").get(0).asInt());
            assertEquals(106_000_000.0, summary.path("unconnected_penalty").asDouble(), 0.01);
            assertEquals(summary.path("construction_cost").asDouble() + 106_000_000.0,
                    summary.path("calculated_cost").asDouble(), 0.01);
            assertTrue(summary.path("new_network_length").asDouble() > 0);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void addPoint(ArrayNode ring, double longitude, double latitude) {
        ring.addArray().add(longitude).add(latitude);
    }
}
