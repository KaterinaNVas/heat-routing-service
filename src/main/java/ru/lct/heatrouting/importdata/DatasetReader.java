package ru.lct.heatrouting.importdata;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.model.Source;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class DatasetReader {

    private final ObjectMapper objectMapper;
    private final GeoJsonGeometryReader geometryReader;

    public DatasetReader(
            ObjectMapper objectMapper,
            GeoJsonGeometryReader geometryReader
    ) {
        this.objectMapper = objectMapper;
        this.geometryReader = geometryReader;
    }

    public InputDataset read(Path file) {
        InputDataset dataset = new InputDataset();

        try (JsonParser parser =
                     objectMapper.getFactory()
                             .createParser(file.toFile())) {

            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new DatasetReadException(
                        "GeoJSON должен начинаться с JSON object"
                );
            }

            boolean featuresFound = false;

            while (parser.nextToken() != JsonToken.END_OBJECT) {

                String fieldName = parser.currentName();

                parser.nextToken();

                if ("type".equals(fieldName)) {
                    String type = parser.getValueAsString();

                    if (!"FeatureCollection".equals(type)) {
                        throw new DatasetReadException(
                                "Ожидался GeoJSON FeatureCollection, получен: "
                                        + type
                        );
                    }

                } else if ("features".equals(fieldName)) {

                    featuresFound = true;

                    readFeatures(parser, dataset);

                } else {
                    parser.skipChildren();
                }
            }

            if (!featuresFound) {
                throw new DatasetReadException(
                        "В GeoJSON отсутствует массив features"
                );
            }

            return dataset;

        } catch (IOException exception) {
            throw new DatasetReadException(
                    "Не удалось прочитать GeoJSON: " + file,
                    exception
            );
        }
    }

    private void readFeatures(
            JsonParser parser,
            InputDataset dataset
    ) throws IOException {

        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new DatasetReadException(
                    "Поле features должно быть массивом"
            );
        }

        while (parser.nextToken() != JsonToken.END_ARRAY) {

            JsonNode feature = objectMapper.readTree(parser);

            readFeature(feature, dataset);
        }
    }

    private void readFeature(
            JsonNode feature,
            InputDataset dataset
    ) {
        JsonNode properties = feature.get("properties");

        if (properties == null || properties.isNull()) {
            throw new DatasetReadException(
                    "Feature не содержит properties"
            );
        }

        String objectType =
                requiredText(properties, "object_type");

        String id =
                requiredId(properties);

        Geometry geometry =
                geometryReader.read(feature.get("geometry"));

        switch (objectType) {

            case "source":
                dataset.setSource(
                        readSource(id, geometry)
                );
                break;

            case "heat_network":
                dataset.getHeatNetwork().add(
                        readHeatNetwork(id, properties, geometry)
                );
                break;

            case "heat_chamber":
                dataset.getHeatChambers().add(
                        readHeatChamber(id, properties, geometry)
                );
                break;

            case "oks_connection_point":
                dataset.getConnectionPoints().add(
                        readConnectionPoint(
                                id,
                                properties,
                                geometry
                        )
                );
                break;

            case "restriction":
                dataset.getRestrictions().add(
                        readRestriction(
                                id,
                                properties,
                                geometry
                        )
                );
                break;

            default:
                throw new DatasetReadException(
                        "Неизвестный object_type: "
                                + objectType
                                + ", id="
                                + id
                );
        }
    }

    private Source readSource(
            String id,
            Geometry geometry
    ) {
        requireGeometry(
                geometry,
                Point.class,
                "source",
                id
        );

        return new Source(
                id,
                (Point) geometry
        );
    }

    private HeatNetworkSegment readHeatNetwork(
            String id,
            JsonNode properties,
            Geometry geometry
    ) {
        requireGeometry(
                geometry,
                LineString.class,
                "heat_network",
                id
        );

        return new HeatNetworkSegment(
                id,
                nullableInteger(properties, "diameter"),
                nullableDouble(properties, "flow_tph"),
                nullableText(properties, "upstream_object_id"),
                (LineString) geometry
        );
    }

    private HeatChamber readHeatChamber(
            String id,
            JsonNode properties,
            Geometry geometry
    ) {
        requireGeometry(
                geometry,
                Point.class,
                "heat_chamber",
                id
        );

        return new HeatChamber(
                id,
                nullableInteger(properties, "diameter"),
                nullableText(properties, "upstream_object_id"),
                (Point) geometry
        );
    }

    private ConnectionPoint readConnectionPoint(
            String id,
            JsonNode properties,
            Geometry geometry
    ) {
        requireGeometry(
                geometry,
                Point.class,
                "oks_connection_point",
                id
        );

        Double flowTph =
                nullableDouble(properties, "flow_tph");

        if (flowTph == null) {
            throw new DatasetReadException(
                    "Для oks_connection_point "
                            + id
                            + " отсутствует flow_tph"
            );
        }

        return new ConnectionPoint(
                id,
                flowTph,
                nullableText(properties, "oks_id"),
                (Point) geometry
        );
    }

    private Restriction readRestriction(
            String id,
            JsonNode properties,
            Geometry geometry
    ) {
        String restrictionTypeValue =
                nullableText(
                        properties,
                        "restriction_type"
                );

        return new Restriction(
                id,
                RestrictionType.fromValue(
                        restrictionTypeValue
                ),
                nullableText(properties, "address"),
                geometry
        );
    }

    private void requireGeometry(
            Geometry geometry,
            Class<? extends Geometry> expectedClass,
            String objectType,
            String id
    ) {
        if (!expectedClass.isInstance(geometry)) {
            throw new DatasetReadException(
                    "Некорректная geometry для "
                            + objectType
                            + " id="
                            + id
                            + ". Ожидалось "
                            + expectedClass.getSimpleName()
                            + ", получено "
                            + geometry.getGeometryType()
            );
        }
    }

    private String requiredId(JsonNode properties) {
        JsonNode value = properties.get("id");

        if (value == null || value.isNull()) {
            throw new DatasetReadException(
                    "Feature не содержит обязательный id"
            );
        }

        return value.asText();
    }

    private String requiredText(
            JsonNode node,
            String fieldName
    ) {
        String value = nullableText(node, fieldName);

        if (value == null || value.isBlank()) {
            throw new DatasetReadException(
                    "Отсутствует обязательное поле: "
                            + fieldName
            );
        }

        return value;
    }

    private String nullableText(
            JsonNode node,
            String fieldName
    ) {
        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private Integer nullableInteger(
            JsonNode node,
            String fieldName
    ) {
        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asInt();
    }

    private Double nullableDouble(
            JsonNode node,
            String fieldName
    ) {
        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        return value.asDouble();
    }
}