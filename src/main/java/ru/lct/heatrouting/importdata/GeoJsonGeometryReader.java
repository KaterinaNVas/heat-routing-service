package ru.lct.heatrouting.importdata;

import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

@Component
public class GeoJsonGeometryReader {

    private static final int WGS84_SRID = 4326;

    private final GeometryFactory geometryFactory =
            new GeometryFactory(
                    new PrecisionModel(PrecisionModel.FLOATING),
                    WGS84_SRID
            );

    public Geometry read(JsonNode geometryNode) {
        if (geometryNode == null || geometryNode.isNull()) {
            throw new DatasetReadException(
                    "У объекта отсутствует geometry"
            );
        }

        String type = requiredText(geometryNode, "type");
        JsonNode coordinates = geometryNode.get("coordinates");

        if (coordinates == null || coordinates.isNull()) {
            throw new DatasetReadException(
                    "У geometry отсутствует coordinates"
            );
        }

        Geometry geometry;

        switch (type) {
            case "Point":
                geometry = readPoint(coordinates);
                break;

            case "LineString":
                geometry = readLineString(coordinates);
                break;

            case "Polygon":
                geometry = readPolygon(coordinates);
                break;

            case "MultiPolygon":
                geometry = readMultiPolygon(coordinates);
                break;

            default:
                throw new DatasetReadException(
                        "Неподдерживаемый тип geometry: " + type
                );
        }

        geometry.setSRID(WGS84_SRID);

        if (!geometry.isValid()) {
            throw new DatasetReadException(
                    "Входной GeoJSON содержит невалидную геометрию типа "
                            + type
            );
        }

        return geometry;
    }

    private Geometry readPoint(JsonNode coordinates) {
        return geometryFactory.createPoint(
                readCoordinate(coordinates)
        );
    }

    private Geometry readLineString(JsonNode coordinates) {
        return geometryFactory.createLineString(
                readCoordinateArray(coordinates)
        );
    }

    private Polygon readPolygon(JsonNode coordinates) {
        if (!coordinates.isArray() || coordinates.isEmpty()) {
            throw new DatasetReadException(
                    "Polygon не содержит колец"
            );
        }

        LinearRing shell = geometryFactory.createLinearRing(
                readCoordinateArray(coordinates.get(0))
        );

        LinearRing[] holes =
                new LinearRing[Math.max(0, coordinates.size() - 1)];

        for (int i = 1; i < coordinates.size(); i++) {
            holes[i - 1] = geometryFactory.createLinearRing(
                    readCoordinateArray(coordinates.get(i))
            );
        }

        return geometryFactory.createPolygon(shell, holes);
    }

    private Geometry readMultiPolygon(JsonNode coordinates) {
        if (!coordinates.isArray()) {
            throw new DatasetReadException(
                    "MultiPolygon должен содержать массив Polygon"
            );
        }

        Polygon[] polygons = new Polygon[coordinates.size()];

        for (int i = 0; i < coordinates.size(); i++) {
            polygons[i] = readPolygon(coordinates.get(i));
        }

        return geometryFactory.createMultiPolygon(polygons);
    }

    private Coordinate[] readCoordinateArray(JsonNode node) {
        if (!node.isArray()) {
            throw new DatasetReadException(
                    "Ожидался массив координат"
            );
        }

        Coordinate[] result = new Coordinate[node.size()];

        for (int i = 0; i < node.size(); i++) {
            result[i] = readCoordinate(node.get(i));
        }

        return result;
    }

    private Coordinate readCoordinate(JsonNode node) {
        if (!node.isArray() || node.size() < 2) {
            throw new DatasetReadException(
                    "Координата должна содержать как минимум X и Y"
            );
        }

        return new Coordinate(
                node.get(0).asDouble(),
                node.get(1).asDouble()
        );
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);

        if (value == null || value.isNull()) {
            throw new DatasetReadException(
                    "Отсутствует обязательное поле: " + fieldName
            );
        }

        return value.asText();
    }
}