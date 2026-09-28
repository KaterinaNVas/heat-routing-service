package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOksVariantsPreviewServiceTest {
    @Test
    void returnsRankedGeoJsonWithConsistentCostsForEachVariant() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CoordinateTransformService coordinates = new CoordinateTransformService();
        VariantCalculator pricing = new VariantCalculator(0.7, 0.3);
        MultiOksPreviewService service = new MultiOksPreviewService(mapper,
                new DatasetReader(mapper, new GeoJsonGeometryReader()),
                new DatasetCoordinateTransformService(coordinates), coordinates,
                new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                        new ConnectionResolver(), new OksConnectionGrouper()),
                new MultiConnectionDraftBuilder(),
                new MultiTreeVariantCalculator(new MultiConnectionCalculator(
                        new FlowPropagator(), pricing, new TopologyValidator()), pricing), pricing);
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        JsonNode result = service.calculateVariants(Path.of(resource.toURI()));
        JsonNode input = mapper.readTree(Path.of(resource.toURI()).toFile());
        Map<String, JsonNode> inputPoints = new HashMap<>();
        for (JsonNode feature : input.path("features")) {
            String type = feature.path("properties").path("object_type").asText();
            if ("oks_connection_point".equals(type) || "heat_chamber".equals(type)) {
                inputPoints.put(feature.path("properties").path("id").asText(),
                        feature.path("geometry").path("coordinates"));
            }
        }

        Map<String, JsonNode> outputNodes = new HashMap<>();
        Map<String, Double> networkCosts = new HashMap<>();
        Map<String, Double> chamberCosts = new HashMap<>();
        Map<String, Integer> routeCounts = new HashMap<>();
        Map<String, Double> networkLengths = new HashMap<>();
        Set<String> summaries = new HashSet<>();
        double previousScore = Double.NEGATIVE_INFINITY;
        int expectedRank = 0;
        for (JsonNode feature : result.path("features")) {
            JsonNode properties = feature.path("properties");
            String variant = properties.path("variant_id").asText();
            switch (properties.path("object_type").asText()) {
                case "heat_network":
                    String startId = properties.path("start_node_id").asText();
                    JsonNode start = inputPoints.containsKey(startId) ? inputPoints.get(startId)
                            : outputNodes.get(variant + ":" + startId);
                    assertNotNull(start, "Неизвестный начальный узел");
                    JsonNode line = feature.path("geometry").path("coordinates");
                    JsonNode lineStart = line.get(0);
                    assertEquals(start.get(0).asDouble(), lineStart.get(0).asDouble(), 1e-7);
                    assertEquals(start.get(1).asDouble(), lineStart.get(1).asDouble(), 1e-7);
                    networkCosts.merge(variant, properties.path("cost").asDouble(), Double::sum);
                    networkLengths.merge(variant, properties.path("length").asDouble(), Double::sum);
                    if (inputPoints.containsKey(startId)) routeCounts.merge(variant, 1, Integer::sum);
                    break;
                case "heat_chamber":
                    chamberCosts.merge(variant, properties.path("cost").asDouble(), Double::sum);
                    outputNodes.put(variant + ":" + properties.path("id").asText(),
                            feature.path("geometry").path("coordinates"));
                    break;
                case "technical_node":
                    outputNodes.put(variant + ":" + properties.path("id").asText(),
                            feature.path("geometry").path("coordinates"));
                    break;
                case "variant_summary":
                    assertTrue(summaries.add(variant));
                    assertEquals(++expectedRank, properties.path("rank").asInt());
                    double score = properties.path("score").asDouble();
                    assertEquals(0.7 * properties.path("calculated_cost").asDouble() / 25_000_000
                                    + 0.3 * properties.path("new_network_length").asDouble() / 100,
                            score, 1e-9);
                    assertTrue(score >= previousScore);
                    previousScore = score;
                    assertEquals(0, properties.path("unconnected_oks_ids").size());
                    assertEquals(17, routeCounts.getOrDefault(variant, 0));
                    assertEquals(networkLengths.getOrDefault(variant, 0.0),
                            properties.path("new_network_length").asDouble(), 0.01);
                    assertEquals(networkCosts.getOrDefault(variant, 0.0)
                                    + chamberCosts.getOrDefault(variant, 0.0)
                                    + properties.path("existing_chamber_tie_in_cost").asDouble(),
                            properties.path("construction_cost").asDouble(), 0.01);
                    break;
                default:
                    throw new AssertionError("Неожиданный тип объекта");
            }
        }
        for (JsonNode feature : result.path("features")) {
            JsonNode props = feature.path("properties");
            if (!"heat_network".equals(props.path("object_type").asText())) continue;
            String variant = props.path("variant_id").asText();
            String endId = props.path("end_node_id").asText();
            JsonNode end = inputPoints.containsKey(endId) ? inputPoints.get(endId)
                    : outputNodes.get(variant + ":" + endId);
            assertNotNull(end, "Неизвестный конечный узел: " + endId);
            JsonNode line = feature.path("geometry").path("coordinates");
            JsonNode lineEnd = line.get(line.size() - 1);
            assertEquals(end.get(0).asDouble(), lineEnd.get(0).asDouble(), 1e-7);
            assertEquals(end.get(1).asDouble(), lineEnd.get(1).asDouble(), 1e-7);
        }
        assertTrue(expectedRank >= 1 && expectedRank <= 2);
        assertTrue(summaries.contains("v1"));
    }
}
