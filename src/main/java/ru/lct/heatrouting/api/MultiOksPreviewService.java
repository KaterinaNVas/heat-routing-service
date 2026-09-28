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
import ru.lct.heatrouting.calculation.SpecialCrossingCostCalculator;
import ru.lct.heatrouting.geo.CoordinateTransformService;
import ru.lct.heatrouting.geo.DatasetCoordinateTransformService;
import ru.lct.heatrouting.importdata.DatasetReader;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.network.MultiConnectionDraftBuilder;
import ru.lct.heatrouting.network.MultiOksRoutePreparationService;
import ru.lct.heatrouting.network.NewSegment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
    private final SpecialCrossingCostCalculator specialCosts = new SpecialCrossingCostCalculator();

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
        return calculate(geoJson, MultiOksRoutePreparationService.RoutePreference.SHORTEST, "v1");
    }

    /** Returns distinct route choices in one GeoJSON FeatureCollection. */
    public ObjectNode calculateVariants(Path geoJson) throws IOException {
        ObjectNode shortest = calculate(geoJson,
                MultiOksRoutePreparationService.RoutePreference.SHORTEST, "v1");
        ObjectNode economical = calculate(geoJson,
                MultiOksRoutePreparationService.RoutePreference.LOWEST_STANDALONE_COST, "v2");
        List<ObjectNode> variants = new ArrayList<>();
        variants.add(shortest);
        if (!networkSignature(shortest).equals(networkSignature(economical))) {
            variants.add(economical);
        }
        variants.sort(Comparator.comparingDouble(collection ->
                summaryProperties(collection).path("score").asDouble()));
        ObjectNode output = mapper.createObjectNode();
        output.put("type", "FeatureCollection");
        ArrayNode features = output.putArray("features");
        int rank = 0;
        for (ObjectNode variant : variants) {
            ObjectNode summary = summaryProperties(variant);
            summary.put("rank", ++rank);
            summary.put("route_strategy", "v1".equals(summary.path("variant_id").asText())
                    ? "SHORTEST" : "LOWEST_STANDALONE_COST");
            for (JsonNode feature : variant.path("features")) features.add(feature);
        }
        return output;
    }

    private List<String> networkSignature(ObjectNode collection) {
        List<String> signature = new ArrayList<>();
        for (JsonNode feature : collection.path("features")) {
            JsonNode properties = feature.path("properties");
            if ("heat_network".equals(properties.path("object_type").asText())) {
                signature.add(properties.path("laying_method").asText() + ":"
                        + feature.path("geometry").toString());
            }
        }
        signature.sort(String::compareTo);
        return signature;
    }

    private ObjectNode summaryProperties(ObjectNode collection) {
        for (JsonNode feature : collection.path("features")) {
            if ("variant_summary".equals(feature.path("properties").path("object_type").asText())) {
                return (ObjectNode) feature.path("properties");
            }
        }
        throw new IllegalStateException("Итог варианта не найден");
    }

    private ObjectNode calculate(Path geoJson,
                                 MultiOksRoutePreparationService.RoutePreference preference,
                                 String variantId) throws IOException {
        InputDataset metric = datasets.toMetric(reader.read(geoJson));
        JsonNode original = mapper.readTree(geoJson.toFile());
        MultiOksRoutePreparationService.Preparation prepared = preparation.prepare(metric, preference);
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
        VariantSummary result = calculator.calculateVariant(variantId, drafts, missingIds, missingFlows,
                metric.getRestrictions());
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
                ObjectNode chamber = feature(draft.getConnection().getChamberId(), "heat_chamber", variantId);
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
            for (Edge edge : draft.getParentEdge().values()) {
                Node node = edge.getTo();
                if (node.equals(draft.getRoot()) || !chambersWritten.add(node.getId())) continue;
                ObjectNode chamber = feature(node.getId(), "heat_chamber", variantId);
                int diameter = draft.getParentEdge().values().stream()
                        .filter(incident -> incident.getFrom().equals(node)
                                || incident.getTo().equals(node))
                        .map(Edge::getId).map(selected::get).filter(s -> s != null)
                        .mapToInt(NewSegment::getDiameter).max().orElse(0);
                ObjectNode chamberProps = (ObjectNode) chamber.get("properties");
                chamberProps.put("diameter", diameter);
                chamberProps.put("cost", variantCosts.chamberCost(diameter));
                chamber.set("geometry", pointGeometry(
                        coordinates.toWgs84(node.getPoint()).getCoordinate()));
                features.add(chamber);
            }
        }
        for (NewSegment segment : result.getSegments()) {
            Edge edge = edges.get(segment.getId());
            Node from = edge.getFrom();
            JsonNode startId = "oks_connection_point".equals(from.getObjectType())
                    ? inputId(original, "oks_connection_point", from.getId().substring("oks:".length()))
                    : mapper.getNodeFactory().textNode(from.getId());
            MultiConnectionDraftBuilder.Draft tree = drafts.stream()
                    .filter(d -> d.getParentEdge().containsValue(edge)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Дерево участка не найдено"));
            Node to = edge.getTo();
            JsonNode endId = to.equals(tree.getRoot()) && tree.getConnection().isExistingChamber()
                    ? inputId(original, "heat_chamber", tree.getConnection().getChamberId())
                    : mapper.getNodeFactory().textNode(to.getId());
            appendNetworkParts(features, segment, edge, startId, endId,
                    result.getFlowsByEdgeId().get(segment.getId()), variantId, metric.getRestrictions());
        }
        ObjectNode summary = feature(variantId + "_summary", "variant_summary", variantId);
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

    /** Exports one priced pipe as separate network features at each special boundary. */
    void appendNetworkParts(ArrayNode features, NewSegment segment, Edge edge,
                            JsonNode startId, JsonNode endId, double flow,
                            String variantId, List<Restriction> restrictions) {
            List<SpecialCrossingCostCalculator.Part> parts = specialCosts.parts(segment,
                    edge.getGeometry(), restrictions);
            if (parts.isEmpty()) throw new IllegalStateException("Пустой участок: " + segment.getId());
            for (int i = 0; i < parts.size(); i++) {
                SpecialCrossingCostCalculator.Part part = parts.get(i);
                String partId = parts.size() == 1 ? segment.getId() : segment.getId() + "_part_" + (i + 1);
                JsonNode nextId = i == parts.size() - 1 ? endId
                        : mapper.getNodeFactory().textNode(variantId + "_technical_" + segment.getId() + "_" + (i + 1));
                ObjectNode network = feature(partId, "heat_network", variantId);
                ObjectNode props = (ObjectNode) network.get("properties");
                props.set("start_node_id", startId.deepCopy());
                props.set("end_node_id", nextId.deepCopy());
                props.put("flow_tph", flow);
                props.put("diameter", segment.getDiameter());
                props.put("length", part.getGeometry().getLength());
                props.put("laying_method", part.isSpecial() ? "special" : "base");
                props.putNull("depth_start");
                props.putNull("depth_end");
                props.put("cost", part.getCost());
                network.set("geometry", lineGeometry((LineString) coordinates.toWgs84(part.getGeometry())));
                features.add(network);
                if (i < parts.size() - 1) {
                    ObjectNode node = feature(nextId.asText(), "technical_node", variantId);
                    node.set("geometry", pointGeometry(coordinates.toWgs84(
                            part.getGeometry().getEndPoint()).getCoordinate()));
                    features.add(node);
                }
                startId = nextId;
            }
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

    private ObjectNode feature(String id, String type, String variantId) {
        ObjectNode feature = mapper.createObjectNode();
        feature.put("type", "Feature");
        ObjectNode props = feature.putObject("properties");
        props.put("id", id);
        props.put("object_type", type);
        props.put("variant_id", variantId);
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
