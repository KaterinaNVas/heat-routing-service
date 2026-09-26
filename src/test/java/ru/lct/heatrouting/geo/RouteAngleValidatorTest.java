package ru.lct.heatrouting.geo;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteAngleValidatorTest {

    private final GeometryFactory gf = new GeometryFactory();
    private final RouteAngleValidator validator = new RouteAngleValidator();

    @Test
    void straightLineHasNoTurns() {
        LineString line = line(
                new Coordinate(0, 0),
                new Coordinate(100, 0)
        );
        RouteValidationResult result = validator.validate(line);
        assertTrue(result.isValid());
        assertEquals(0, result.getAngles().size());
    }

    @Test
    void rightAngleIsAllowed() {
        LineString line = line(
                new Coordinate(0, 0),
                new Coordinate(100, 0),
                new Coordinate(100, 100)
        );
        RouteValidationResult result = validator.validate(line);
        assertTrue(result.isValid());
        assertEquals(90.0, result.getMaxAngle(), 1e-6);
    }

    @Test
    void acuteAngleIsAllowed() {
        LineString line = line(
                new Coordinate(0, 0),
                new Coordinate(100, 0),
                new Coordinate(200, 100)
        );
        RouteValidationResult result = validator.validate(line);
        assertTrue(result.isValid());
        assertEquals(45.0, result.getMaxAngle(), 1e-6);
    }

    @Test
    void obtuseAngleIsForbidden() {
        LineString line = line(
                new Coordinate(0, 0),
                new Coordinate(100, 0),
                new Coordinate(0, 100)
        );
        RouteValidationResult result = validator.validate(line);
        assertFalse(result.isValid());
        assertEquals(135.0, result.getMaxAngle(), 1e-6);
    }

    @Test
    void selfIntersectionIsForbidden() {
        LineString line = line(
                new Coordinate(0, 0),
                new Coordinate(100, 100),
                new Coordinate(0, 100),
                new Coordinate(100, 0)
        );
        RouteValidationResult result = validator.validate(line);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream()
                .anyMatch(e -> e.contains("self-intersections")));
    }

    @Test
    void multipleTurnsAreChecked() {
        LineString line = line(
                new Coordinate(0, 0),
                new Coordinate(100, 0),
                new Coordinate(100, 100),
                new Coordinate(200, 100),
                new Coordinate(200, 200)
        );
        RouteValidationResult result = validator.validate(line);
        assertTrue(result.isValid());
        assertEquals(3, result.getAngles().size());
        assertEquals(90.0, result.getMaxAngle(), 1e-6);
    }

    private LineString line(Coordinate... coords) {
        LineString line = gf.createLineString(coords);
        line.setSRID(32637);
        return line;
    }
}
