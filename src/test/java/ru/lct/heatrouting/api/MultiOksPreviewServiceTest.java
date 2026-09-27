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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOksPreviewServiceTest {
    @Test
    void exportsRealDatasetWithOneSummaryAndNoDuplicatedCosts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CoordinateTransformService coords = new CoordinateTransformService();
        VariantCalculator pricing = new VariantCalculator(0.3, 0.7);
        MultiOksPreviewService service = new MultiOksPreviewService(mapper,
                new DatasetReader(mapper, new GeoJsonGeometryReader()),
                new DatasetCoordinateTransformService(coords), coords,
                new MultiOksRoutePreparationService(new TerritoryRoutePlanner(),
                        new ConnectionResolver(), new OksConnectionGrouper()),
                new MultiConnectionDraftBuilder(),
                new MultiTreeVariantCalculator(new MultiConnectionCalculator(
                        new FlowPropagator(), pricing, new TopologyValidator()), pricing), pricing);
        URL resource = getClass().getClassLoader().getResource("test-dataset.geojson");
        assertNotNull(resource);
        JsonNode output = service.calculate(Path.of(resource.toURI()));
        double segments = 0;
        double chambers = 0;
        JsonNode summary = null;
        int networkCount = 0;
        for (JsonNode feature : output.path("features")) {
            JsonNode props = feature.path("properties");
            switch (props.path("object_type").asText()) {
                case "heat_network":
                    networkCount++;
                    assertEquals("LineString", feature.path("geometry").path("type").asText());
                    segments += props.path("cost").asDouble();
                    break;
                case "heat_chamber":
                    chambers += props.path("cost").asDouble();
                    break;
                case "variant_summary":
                    summary = props;
                    break;
                default:
                    throw new AssertionError("Лишний тип объекта");
            }
        }
        assertNotNull(summary);
        assertEquals(17, networkCount);
        assertEquals(0, summary.path("unconnected_oks_ids").size());
        assertEquals(segments + chambers + summary.path("existing_chamber_tie_in_cost").asDouble(),
                summary.path("construction_cost").asDouble(), 0.01);
        assertTrue(Double.isFinite(summary.path("score").asDouble()));
    }
}
