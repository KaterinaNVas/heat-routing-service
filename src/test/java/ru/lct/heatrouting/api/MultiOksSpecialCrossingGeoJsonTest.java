package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.calculation.SpecialCrossingCostCalculator;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.network.NewSegment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MultiOksSpecialCrossingGeoJsonTest {
    @Test
    void exportsBaseSpecialBaseWithTwoMatchingTechnicalNodes() {
        GeometryFactory geometry = new GeometryFactory();
        LineString route = geometry.createLineString(new Coordinate[]{
                new Coordinate(400000, 6173000), new Coordinate(400020, 6173000)});
        route.setSRID(32637);
        Polygon road = geometry.createPolygon(new Coordinate[]{
                new Coordinate(400006, 6172999), new Coordinate(400010, 6172999),
                new Coordinate(400010, 6173001), new Coordinate(400006, 6173001),
                new Coordinate(400006, 6172999)});
        road.setSRID(32637);
        Restriction restriction = new Restriction("road", RestrictionType.ROAD, null, road);
        Node from = new Node("oks:1", route.getStartPoint(), "oks_connection_point");
        Node to = new Node("chamber", route.getEndPoint(), "heat_chamber");
        Edge edge = new Edge("pipe", from, to, route, 100, 0, "base");
        NewSegment segment = new NewSegment("pipe", 100, 20);
        ObjectMapper mapper = new ObjectMapper();
        CoordinateTransformService coordinates = new CoordinateTransformService();
        MultiOksPreviewService service = new MultiOksPreviewService(mapper, null, null,
                coordinates, null, null, null, null);
        ArrayNode output = mapper.createArrayNode();
        service.appendNetworkParts(output, segment, edge, mapper.getNodeFactory().numberNode(1),
                mapper.getNodeFactory().textNode("chamber"), 12, "v1", List.of(restriction));

        assertEquals(5, output.size());
        Map<String, JsonNode> nodes = new HashMap<>();
        double cost = 0;
        double length = 0;
        int networks = 0, technicalNodes = 0;
        for (JsonNode feature : output) {
            JsonNode properties = feature.path("properties");
            if ("technical_node".equals(properties.path("object_type").asText())) {
                technicalNodes++;
                nodes.put(properties.path("id").asText(), feature.path("geometry").path("coordinates"));
            }
        }
        for (JsonNode feature : output) {
            JsonNode properties = feature.path("properties");
            if (!"heat_network".equals(properties.path("object_type").asText())) continue;
            int piece = networks++;
            assertEquals(piece == 1 ? "special" : "base", properties.path("laying_method").asText());
            JsonNode line = feature.path("geometry").path("coordinates");
            String start = properties.path("start_node_id").asText();
            String end = properties.path("end_node_id").asText();
            if (piece == 0) assertEquals("1", start);
            if (piece == 2) assertEquals("chamber", end);
            JsonNode startPoint = piece == 0 ? line.get(0) : nodes.get(start);
            JsonNode endPoint = piece == 2 ? line.get(line.size() - 1) : nodes.get(end);
            assertNotNull(startPoint);
            assertNotNull(endPoint);
            assertEquals(startPoint.get(0).asDouble(), line.get(0).get(0).asDouble(), 1e-7);
            assertEquals(startPoint.get(1).asDouble(), line.get(0).get(1).asDouble(), 1e-7);
            assertEquals(endPoint.get(0).asDouble(), line.get(line.size() - 1).get(0).asDouble(), 1e-7);
            assertEquals(endPoint.get(1).asDouble(), line.get(line.size() - 1).get(1).asDouble(), 1e-7);
            length += properties.path("length").asDouble();
            cost += properties.path("cost").asDouble();
        }
        assertEquals(3, networks);
        assertEquals(2, technicalNodes);
        assertEquals(20, length, 1e-7);
        assertEquals(new SpecialCrossingCostCalculator().calculate(segment, route,
                List.of(restriction)), cost, 0.01);
    }
}
