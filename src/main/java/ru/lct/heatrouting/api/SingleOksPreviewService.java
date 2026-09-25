package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.lct.heatrouting.calculation.SingleConnectionCalculator;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.network.ConnectionResolver;
import ru.lct.heatrouting.network.SingleConnectionDraftBuilder;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Пробный расчёт для одного выбранного ОКС, без ранжирования полного набора. */
@Service
public class SingleOksPreviewService {
    private final ObjectMapper mapper;
    private final DatasetReader reader;
    private final DatasetCoordinateTransformService datasets;
    private final CoordinateTransformService coordinates;
    private final TerritoryRoutePlanner planner;
    private final ConnectionResolver resolver;
    private final SingleConnectionDraftBuilder drafts;
    private final SingleConnectionCalculator calculator;
    private final DiameterCatalog diameters = new DiameterCatalog();

    public SingleOksPreviewService(ObjectMapper mapper, DatasetReader reader,
                                   DatasetCoordinateTransformService datasets,
                                   CoordinateTransformService coordinates,
                                   TerritoryRoutePlanner planner, ConnectionResolver resolver,
                                   SingleConnectionDraftBuilder drafts,
                                   SingleConnectionCalculator calculator) {
        this.mapper = mapper;
        this.reader = reader;
        this.datasets = datasets;
        this.coordinates = coordinates;
        this.planner = planner;
        this.resolver = resolver;
        this.drafts = drafts;
        this.calculator = calculator;
    }

    public ObjectNode calculate(Path geoJson, String oksId) throws IOException {
        InputDataset metric = datasets.toMetric(reader.read(geoJson));
        ConnectionPoint oks = metric.getConnectionPoints().stream()
                .filter(p -> Objects.equals(p.getId(), oksId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("ОКС не найден: " + oksId));
        JsonNode original = mapper.readTree(geoJson.toFile());
        JsonNode originalOksId = inputId(original, "oks_connection_point", oksId);
        int diameter = diameters.findMinimalDiameter(oks.getFlowTph());
        for (int attempt = 0; attempt < 18; attempt++) {
            final int candidateDiameter = diameter;
            TerritoryRoutePlanner.Route route = planner.find(metric, oks, candidateDiameter)
                    .orElseThrow(() -> new IllegalArgumentException("Допустимый маршрут не найден"));
            ConnectionResolver.Connection connection = resolver.resolve(metric, route, "v1_chamber_1");
            if (connection.isExistingChamber() &&
                    route.getGeometry().getEndPoint().distance(connection.getPoint()) > 0.01) {
                final Point chosenChamber = connection.getPoint();
                String chamberSegment = metric.getHeatNetwork().stream()
                        .filter(s -> s.getGeometry().distance(chosenChamber) <= 0.01)
                        .map(HeatNetworkSegment::getId).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Камера не лежит на существующей сети"));
                route = planner.findTo(metric, oks, candidateDiameter,
                                chosenChamber, chamberSegment)
                        .orElseThrow(() -> new IllegalArgumentException("Нет пути к выбранной камере"));
                connection = resolver.resolve(metric, route, "v1_chamber_1");
            }
            SingleConnectionDraftBuilder.Draft draft = drafts.build(oks, route, connection,
                    "v1_net_1", candidateDiameter);
            try {
                SingleConnectionCalculator.CalculatedConnection result = calculator.calculate(draft);
                JsonNode chamberId = connection.isExistingChamber()
                        ? inputId(original, "heat_chamber", connection.getChamberId())
                        : mapper.getNodeFactory().textNode(connection.getChamberId());
                return export(draft, result, originalOksId, chamberId);
            } catch (SingleConnectionCalculator.DiameterChangeRequiredException exception) {
                diameter = exception.getRequiredDiameter();
            }
        }
        throw new IllegalArgumentException("Не удалось согласовать ДУ и геометрию маршрута");
    }

    private JsonNode inputId(JsonNode collection, String type, String id) {
        JsonNode found = null;
        for (JsonNode feature : collection.path("features")) {
            JsonNode properties = feature.path("properties");
            if (type.equals(properties.path("object_type").asText()) &&
                    id.equals(properties.path("id").asText())) {
                if (found != null) throw new IllegalArgumentException("Неоднозначный id: " + id);
                found = properties.get("id");
            }
        }
        if (found == null) throw new IllegalArgumentException("id не найден: " + id);
        return found;
    }

    private ObjectNode export(SingleConnectionDraftBuilder.Draft draft,
                              SingleConnectionCalculator.CalculatedConnection result,
                              JsonNode oksId, JsonNode chamberId) {
        ObjectNode output = mapper.createObjectNode();
        output.put("type", "FeatureCollection");
        ArrayNode features = output.putArray("features");
        LineString line = (LineString) coordinates.toWgs84(result.getEdge().getGeometry());
        ObjectNode network = feature("v1_net_1", "heat_network");
        ObjectNode netProperties = (ObjectNode) network.get("properties");
        netProperties.set("start_node_id", oksId.deepCopy());
        netProperties.set("end_node_id", chamberId.deepCopy());
        netProperties.put("flow_tph", result.getEdge().getFlowTph());
        netProperties.put("diameter", result.getEdge().getDiameter());
        netProperties.put("length", result.getEdge().getLengthMeters());
        netProperties.put("laying_method", "base");
        netProperties.putNull("depth_start");
        netProperties.putNull("depth_end");
        netProperties.put("cost", result.getSegmentCost());
        network.set("geometry", lineGeometry(line));
        features.add(network);

        if (!draft.getConnection().isExistingChamber()) {
            ObjectNode chamber = feature(draft.getConnection().getChamberId(), "heat_chamber");
            ObjectNode properties = (ObjectNode) chamber.get("properties");
            properties.put("diameter", result.getEdge().getDiameter());
            properties.put("cost", result.getChamberCost());
            chamber.set("geometry", pointGeometry(coordinates.toWgs84(draft.getConnection().getPoint())));
            features.add(chamber);
        }
        ObjectNode summary = feature("v1_summary", "variant_summary");
        ObjectNode p = (ObjectNode) summary.get("properties");
        p.put("rank", 1);
        p.put("construction_cost", result.getConstructionCost());
        p.put("chamber_construction_cost", result.getChamberCost());
        p.put("existing_chamber_tie_in_count", result.getExistingChamberTieInCount());
        p.put("existing_chamber_tie_in_cost", 5_000_000.0 * result.getExistingChamberTieInCount());
        p.put("unconnected_penalty", 0);
        p.put("calculated_cost", result.getConstructionCost());
        p.put("new_network_length", result.getEdge().getLengthMeters());
        p.put("score", result.getScore());
        p.putArray("unconnected_oks_ids");
        summary.putNull("geometry");
        features.add(summary);
        return output;
    }

    private ObjectNode feature(String id, String type) {
        ObjectNode feature = mapper.createObjectNode();
        feature.put("type", "Feature");
        ObjectNode properties = feature.putObject("properties");
        properties.put("id", id);
        properties.put("object_type", type);
        properties.put("variant_id", "v1");
        return feature;
    }

    private ObjectNode lineGeometry(LineString line) {
        ObjectNode geometry = mapper.createObjectNode();
        geometry.put("type", "LineString");
        ArrayNode positions = geometry.putArray("coordinates");
        for (Coordinate c : line.getCoordinates()) {
            ArrayNode pair = positions.addArray();
            pair.add(c.x);
            pair.add(c.y);
        }
        return geometry;
    }

    private ObjectNode pointGeometry(Geometry point) {
        ObjectNode geometry = mapper.createObjectNode();
        geometry.put("type", "Point");
        Coordinate c = point.getCoordinate();
        ArrayNode pair = geometry.putArray("coordinates");
        pair.add(c.x);
        pair.add(c.y);
        return geometry;
    }
}
