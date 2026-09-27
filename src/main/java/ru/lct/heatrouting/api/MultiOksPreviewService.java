package ru.lct.heatrouting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.lct.heatrouting.calculation.MultiTreeVariantCalculator;
import ru.lct.heatrouting.calculation.VariantSummary;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.network.MultiConnectionDraftBuilder;
import ru.lct.heatrouting.network.MultiOksRoutePreparationService;
import ru.lct.heatrouting.network.NewSegment;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.cost.SegmentCostCalculator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Exports one calculated variant for every input OKS. */
@Service
public class MultiOksPreviewService {
    private final ObjectMapper mapper;
    private final DatasetReader reader;
    private final DatasetCoordinateTransformService datasets;
    private final CoordinateTransformService coordinates;
    private final MultiOksRoutePreparationService preparation;
    private final MultiConnectionDraftBuilder builder;
    private final MultiTreeVariantCalculator calculator;
    private final VariantCalculator variantCosts;
    private final SegmentCostCalculator segmentCosts =
            new SegmentCostCalculator(new DiameterCatalog());

    public MultiOksPreviewService(ObjectMapper mapper, DatasetReader reader,
                                  DatasetCoordinateTransformService datasets,
                                  CoordinateTransformService coordinates,
                                  MultiOksRoutePreparationService preparation,
                                  MultiConnectionDraftBuilder builder,
                                  MultiTreeVariantCalculator calculator,
                                  VariantCalculator variantCosts) {
        this.mapper = mapper;
        this.reader = reader;
        this.datasets = datasets;
        this.coordinates = coordinates;
        this.preparation = preparation;
        this.builder = builder;
        this.calculator = calculator;
        this.variantCosts = variantCosts;
    }

    public ObjectNode calculate(Path geoJson) throws IOException {
        InputDataset metric = datasets.toMetric(reader.read(geoJson));
        JsonNode original = mapper.readTree(geoJson.toFile());
        MultiOksRoutePreparationService.Preparation prepared = preparation.prepare(metric);
        List<MultiConnectionDraftBuilder.Draft> drafts = builder.build(prepared);
        if (drafts.isEmpty()) throw new IllegalArgumentException("Не удалось подключить ни один ОКС");

        Map<String, ConnectionPoint> oksById = metric.getConnectionPoints().stream()
                .collect(Collectors.toMap(ConnectionPoint::getId, Function.identity()));
        List<Object> missingIds = new ArrayList<>();
        List<Double> missingFlows = new ArrayList<>();
        for (String id : prepared.getUnconnectedOksIds()) {
            missingIds.add(inputId(original, "oks_connection_point", id));
            missingFlows.add(oksById.get(id).getFlowTph());
        }
        VariantSummary result = calculator.calculateVariant("v1", drafts, missingIds, missingFlows);
        Map<String, NewSegment> selected = result.getSegments().stream()
                .collect(Collectors.toMap(NewSegment::getId, Function.identity()));

        ObjectNode output = mapper.createObjectNode();
        output.put("type", "FeatureCollection");
        ArrayNode features = output.putArray("features");
        Set<String> chambersWritten = new HashSet<>();
        Map<String, Edge> edges = new HashMap<>();
        for (MultiConnectionDraftBuilder.Draft draft : drafts) {
            for (Edge edge : draft.getParentEdge().values()) edges.put(edge.getId(), edge);
            if (!draft.getConnection().isExistingChamber()
                    && chambersWritten.add(draft.getConnection().getChamberId())) {
                ObjectNode chamber = feature(draft.getConnection().getChamberId(), "heat_chamber");
                ObjectNode props = (ObjectNode) chamber.get("properties");
                int diameter = draft.getParentEdge().values().stream()
                        .map(Edge::getId).map(selected::get).filter(s -> s != null)
                        .mapToInt(NewSegment::getDiameter).max().orElse(0);
                props.put("diameter", diameter);
                props.put("cost", variantCosts.chamberCost(diameter));
                chamber.set("geometry", pointGeometry(
                        coordinates.toWgs84(draft.getConnection().getPoint()).getCoordinate()));
                features.add(chamber);
            }
        }
        for (NewSegment segment : result.getSegments()) {
            Edge edge = edges.get(segment.getId());
            ObjectNode network = feature(segment.getId(), "heat_network");
            ObjectNode props = (ObjectNode) network.get("properties");
            Node from = edge.getFrom();
            String oksId = from.getId().substring("oks:".length());
            props.set("start_node_id", inputId(original, "oks_connection_point", oksId));
            MultiConnectionDraftBuilder.Draft tree = drafts.stream()
                    .filter(d -> d.getParentEdge().containsValue(edge)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Дерево участка не найдено"));
            props.set("end_node_id", tree.getConnection().isExistingChamber()
                    ? inputId(original, "heat_chamber", tree.getConnection().getChamberId())
                    : mapper.getNodeFactory().textNode(tree.getConnection().getChamberId()));
            props.put("flow_tph", result.getFlowsByEdgeId().get(segment.getId()));
            props.put("diameter", segment.getDiameter());
            props.put("length", segment.getLength());
            props.put("laying_method", edge.getLayingMethod());
            props.putNull("depth_start");
            props.putNull("depth_end");
            props.put("cost", segmentCosts.calculateNewSegmentCost(segment));
            network.set("geometry", lineGeometry((LineString) coordinates.toWgs84(edge.getGeometry())));
            features.add(network);
        }
        ObjectNode summary = feature("v1_summary", "variant_summary");
        ObjectNode props = (ObjectNode) summary.get("properties");
        props.put("rank", result.getRank());
        props.put("construction_cost", result.getConstructionCost());
        props.put("chamber_construction_cost", result.getChamberConstructionCost());
        props.put("existing_chamber_tie_in_count", result.getExistingChamberTieInCount());
        props.put("existing_chamber_tie_in_cost", result.getExistingChamberTieInCost());
        props.put("unconnected_penalty", result.getUnconnectedPenalty());
        props.put("calculated_cost", result.getCalculatedCost());
        props.put("new_network_length", result.getNewNetworkLength());
        props.put("score", result.getScore());
        ArrayNode missing = props.putArray("unconnected_oks_ids");
        for (Object id : result.getUnconnectedOksIds()) missing.add((JsonNode) id);
        summary.putNull("geometry");
        features.add(summary);
        return output;
    }

    private JsonNode inputId(JsonNode collection, String type, String id) {
        JsonNode found = null;
        for (JsonNode feature : collection.path("features")) {
            JsonNode props = feature.path("properties");
            if (type.equals(props.path("object_type").asText()) && id.equals(props.path("id").asText())) {
                if (found != null) throw new IllegalArgumentException("Неоднозначный id: " + id);
                found = props.get("id");
            }
        }
        if (found == null) throw new IllegalArgumentException("id не найден: " + id);
        return found.deepCopy();
    }

    private ObjectNode feature(String id, String type) {
        ObjectNode feature = mapper.createObjectNode();
        feature.put("type", "Feature");
        ObjectNode props = feature.putObject("properties");
        props.put("id", id);
        props.put("object_type", type);
        props.put("variant_id", "v1");
        return feature;
    }

    private ObjectNode pointGeometry(Coordinate c) {
        ObjectNode geometry = mapper.createObjectNode();
        geometry.put("type", "Point");
        ArrayNode pair = geometry.putArray("coordinates");
        pair.add(c.x);
        pair.add(c.y);
        return geometry;
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
}
