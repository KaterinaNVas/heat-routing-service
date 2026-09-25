package ru.lct.heatrouting.geo;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RouteAngleValidator {

    private static final Logger log = LoggerFactory.getLogger(RouteAngleValidator.class);

    public static final double MAX_ANGLE_DEGREES = 90.0;
    private static final double EPSILON = 1e-6;

    private final GeometryFactory factory = new GeometryFactory();

    public RouteValidationResult validate(LineString route) {
        if (route == null) return RouteValidationResult.fail("route is null");
        if (route.isEmpty()) return RouteValidationResult.fail("route is empty");
        if (route.getNumPoints() < 2) return RouteValidationResult.fail("route has less than 2 points");

        List<String> errors = new ArrayList<>();
        List<Double> angles = computeAngles(route);

        for (int i = 0; i < angles.size(); i++) {
            if (angles.get(i) > MAX_ANGLE_DEGREES + EPSILON) {
                errors.add(String.format(
                    "turn #%d has angle %.1f > %.0f (forbidden)",
                    i + 1, angles.get(i), MAX_ANGLE_DEGREES
                ));
            }
        }

        if (hasSelfIntersection(route)) {
            errors.add("route has self-intersections outside nodes");
        }

        if (errors.isEmpty()) return RouteValidationResult.ok(angles);
        log.warn("Route validation failed: {}", errors);
        return RouteValidationResult.of(errors, angles);
    }

    public List<Double> computeAngles(LineString line) {
        List<Double> angles = new ArrayList<>();
        Coordinate[] coords = line.getCoordinates();
        for (int i = 1; i < coords.length - 1; i++) {
            angles.add(angleAt(coords[i - 1], coords[i], coords[i + 1]));
        }
        return angles;
    }

    public double angleAt(Coordinate a, Coordinate b, Coordinate c) {
        double v1x = a.x - b.x;
        double v1y = a.y - b.y;
        double v2x = c.x - b.x;
        double v2y = c.y - b.y;

        double dot = v1x * v2x + v1y * v2y;
        double mag1 = Math.hypot(v1x, v1y);
        double mag2 = Math.hypot(v2x, v2y);

        if (mag1 < EPSILON || mag2 < EPSILON) {
            return 0.0;
        }

        double cos = dot / (mag1 * mag2);
        cos = Math.max(-1.0, Math.min(1.0, cos));

        double angleBetweenVectors = Math.toDegrees(Math.acos(cos));

        return 180.0 - angleBetweenVectors;
    }

    public boolean hasSelfIntersection(LineString line) {
        return !line.isSimple();
    }
}
