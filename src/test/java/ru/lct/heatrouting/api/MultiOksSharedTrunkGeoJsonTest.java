package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.calculation.FlowPropagator;
import ru.lct.heatrouting.calculation.MultiConnectionCalculator;
import ru.lct.heatrouting.calculation.MultiTreeVariantCalculator;
import ru.lct.heatrouting.calculation.TopologyValidator;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.importdata.GeoJsonGeometryReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.network.ConnectionResolver;
import ru.lct.heatrouting.network.MultiConnectionDraftBuilder;
import ru.lct.heatrouting.network.MultiOksRoutePreparationService;
import ru.lct.heatrouting.network.OksConnectionGrouper;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiOksSharedTrunkGeoJsonTest {
    private final GeometryFactory geometry = new GeometryFactory();

    @Test
    void exportsOnePricedTrunkAndItsChamber() throws Exception {
        InputDataset metric = new InputDataset();
        metric.getHeatNetwork().add(new HeatNetworkSegment("existing", 100, null, null,
                line(new Coordinate(500010, 6199999), new Coordinate(500010, 6200001))));
        metric.getHeatChambers().add(new HeatChamber("root", 100, null, point(500010, 6200000)));
        metric.getConnectionPoints().add(new ConnectionPoint("a", 2.0, null, point(500000, 6199996)));
        metric.getConnectionPoints().add(new ConnectionPoint("b", 3.0, null, point(500000, 6200004)));

        ObjectMapper mapper = new ObjectMapper();
        CoordinateTransformService coordinates = new CoordinateTransformService();
        DatasetReader reader = new DatasetReader(mapper, new GeoJsonGeometryReader()) {
            @Override public InputDataset read(Path ignored) { return metric; }
        };
        DatasetCoordinateTransformService datasets = new DatasetCoordinateTransformService(coordinates) {
            @Override public InputDataset toMetric(InputDataset ignored) { return metric; }
        };
        TerritoryRoutePlanner planner = new TerritoryRoutePlanner() {
            @Override public Optional<Route> find(InputDataset ignored, ConnectionPoint oks, int diameter) {
                LineString route = line(oks.getGeometry().getCoordinate(),
                        new Coordinate(500005, 6200000), new Coordinate(500010, 6200000));
                try {
                    Constructor<Route> ctor = Route.class.getDeclaredConstructor(
                            LineString.class, String.class, Point.class);
                    ctor.setAccessible(true);
                    return Optional.of(ctor.newInstance(route, "existing", point(500010, 6200000)));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }
        };
        VariantCalculator pricing = new VariantCalculator(0.7, 0.3);
        MultiOksPreviewService service = new MultiOksPreviewService(mapper, reader, datasets,
                coordinates, new MultiOksRoutePreparationService(planner, new ConnectionResolver(),
                new OksConnectionGrouper()), new MultiConnectionDraftBuilder(),
                new MultiTreeVariantCalculator(new MultiConnectionCalculator(
                        new FlowPropagator(), pricing, new TopologyValidator()), pricing), pricing);
        Path input = Files.createTempFile("shared-trunk-", ".geojson");
        try {
            Files.writeString(input, "{\"type\":\"FeatureCollection\",\"features\":["
                    + "{\"properties\":{\"id\":\"a\",\"object_type\":\"oks_connection_point\"}},"
                    + "{\"properties\":{\"id\":\"b\",\"object_type\":\"oks_connection_point\"}},"
                    + "{\"properties\":{\"id\":\"root\",\"object_type\":\"heat_chamber\"}}]}");
            JsonNode output = service.calculate(input);
            Map<String, JsonNode> network = new HashMap<>();
            String junction = null;
            double networkCost = 0;
            double chamberCost = 0;
            JsonNode summary = null;
            for (JsonNode feature : output.path("features")) {
                JsonNode props = feature.path("properties");
                switch (props.path("object_type").asText()) {
                    case "heat_network":
                        network.put(props.path("id").asText(), props);
                        networkCost += props.path("cost").asDouble();
                        break;
                    case "heat_chamber":
                        junction = props.path("id").asText();
                        chamberCost += props.path("cost").asDouble();
                        break;
                    case "variant_summary": summary = props; break;
                    default: throw new AssertionError("Unexpected feature");
                }
            }
            assertEquals(3, network.size());
            assertNotNull(junction);
            String junctionId = junction;
            long branches = network.values().stream()
                    .filter(props -> junctionId.equals(props.path("end_node_id").asText())).count();
            assertEquals(2, branches);
            JsonNode trunk = network.values().stream()
                    .filter(props -> junctionId.equals(props.path("start_node_id").asText()))
                    .findFirst().orElseThrow();
            assertEquals("root", trunk.path("end_node_id").asText());
            assertEquals(5.0, trunk.path("flow_tph").asDouble(), 1e-9);
            assertNotNull(summary);
            assertTrue(chamberCost > 0);
            assertEquals(networkCost + chamberCost
                    + summary.path("existing_chamber_tie_in_cost").asDouble(),
                    summary.path("construction_cost").asDouble(), 0.01);
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private Point point(double x, double y) {
        Point point = geometry.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    private LineString line(Coordinate... coordinates) {
        LineString line = geometry.createLineString(coordinates);
        line.setSRID(32637);
        return line;
    }
}
