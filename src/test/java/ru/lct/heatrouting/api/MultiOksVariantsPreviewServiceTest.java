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
            if ("oks_connection_point".equals(
                    feature.path("properties").path("object_type").asText())) {
                inputPoints.put(feature.path("properties").path("id").asText(),
                        feature.path("geometry").path("coordinates"));
            }
        }

        Map<String, Double> networkCosts = new HashMap<>();
        Map<String, Double> chamberCosts = new HashMap<>();
        Map<String, Integer> routeCounts = new HashMap<>();
        Set<String> summaries = new HashSet<>();
        double previousScore = Double.NEGATIVE_INFINITY;
        int expectedRank = 0;
        for (JsonNode feature : result.path("features")) {
            JsonNode properties = feature.path("properties");
            String variant = properties.path("variant_id").asText();
            switch (properties.path("object_type").asText()) {
                case "heat_network":
                    JsonNode start = inputPoints.get(properties.path("start_node_id").asText());
                    assertNotNull(start, "Неизвестная точка подключения");
                    JsonNode lineStart = feature.path("geometry").path("coordinates").get(0);
                    assertEquals(start.get(0).asDouble(), lineStart.get(0).asDouble(), 1e-7);
                    assertEquals(start.get(1).asDouble(), lineStart.get(1).asDouble(), 1e-7);
                    networkCosts.merge(variant, properties.path("cost").asDouble(), Double::sum);
                    routeCounts.merge(variant, 1, Integer::sum);
                    break;
                case "heat_chamber":
                    chamberCosts.merge(variant, properties.path("cost").asDouble(), Double::sum);
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
                    assertEquals(networkCosts.getOrDefault(variant, 0.0)
                                    + chamberCosts.getOrDefault(variant, 0.0)
                                    + properties.path("existing_chamber_tie_in_cost").asDouble(),
                            properties.path("construction_cost").asDouble(), 0.01);
                    break;
                default:
                    throw new AssertionError("Неожиданный тип объекта");
            }
        }
        assertTrue(expectedRank >= 1 && expectedRank <= 2);
        assertTrue(summaries.contains("v1"));
    }
}
