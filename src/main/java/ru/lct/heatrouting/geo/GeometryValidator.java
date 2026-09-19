package ru.lct.heatrouting.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class GeometryValidator {

    private static final Logger log = LoggerFactory.getLogger(GeometryValidator.class);

    private static final Set<String> LINE_TYPES = Set.of(
        "heat_network"
    );

    private static final Set<String> POLYGON_TYPES = Set.of(
        "oks_future", "oks_existing", "restriction"
    );

    private static final Set<String> POINT_TYPES = Set.of(
        "source", "heat_chamber", "oks_connection_point"
    );

    public ValidationResult validate(Geometry geometry, String objectType) {
        List<String> errors = new ArrayList<>();

        if (geometry == null) {
            return ValidationResult.fail("geometry is null");
        }
        if (geometry.isEmpty()) {
            return ValidationResult.fail("geometry is empty");
        }

        if (objectType != null && !objectType.isBlank()) {
            String typeError = checkType(geometry, objectType);
            if (typeError != null) {
                errors.add(typeError);
            }
        }

        if (!isWithinEpsg4326(geometry)) {
            errors.add("coordinates outside EPSG:4326 range "
                + "(longitude must be in [-180, 180], latitude in [-90, 90])");
        }

        if (geometry instanceof Polygon) {
    Polygon polygon = (Polygon) geometry;
    if (!polygon.isValid()) {
        errors.add("polygon is invalid (self-intersections or unclosed ring)");
    }
}

        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        log.warn("Geometry validation failed for type={}: {}", objectType, errors);
        return ValidationResult.of(errors);
    }

    private String checkType(Geometry geometry, String objectType) {
        if (LINE_TYPES.contains(objectType)) {
            if (!(geometry instanceof LineString)) {
                return "for object_type='" + objectType
                    + "' expected LineString, got " + geometry.getGeometryType();
            }
        } else if (POLYGON_TYPES.contains(objectType)) {
            if (!(geometry instanceof Polygon) && !(geometry instanceof MultiPolygon)) {
                return "for object_type='" + objectType
                    + "' expected Polygon/MultiPolygon, got " + geometry.getGeometryType();
            }
        } else if (POINT_TYPES.contains(objectType)) {
            if (!(geometry instanceof Point)) {
                return "for object_type='" + objectType
                    + "' expected Point, got " + geometry.getGeometryType();
            }
        }
        return null;
    }

    private boolean isWithinEpsg4326(Geometry geometry) {
        for (Coordinate c : geometry.getCoordinates()) {
            if (Double.isNaN(c.x) || Double.isNaN(c.y)) return false;
            if (c.x < -180.0 || c.x > 180.0) return false;
            if (c.y < -90.0 || c.y > 90.0) return false;
        }
        return true;
    }
}