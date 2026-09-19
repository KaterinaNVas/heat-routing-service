package ru.lct.heatrouting.geo;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometryValidatorTest {

    private final GeometryFactory gf = new GeometryFactory();
    private final GeometryValidator validator = new GeometryValidator();

    @Test
    void validPoint() {
        Point p = gf.createPoint(new Coordinate(37.6, 55.7));
        assertTrue(validator.validate(p, "heat_chamber").isValid());
    }

    @Test
    void validLineString() {
        LineString line = gf.createLineString(new Coordinate[]{
            new Coordinate(37.6, 55.7),
            new Coordinate(37.61, 55.71)
        });
        assertTrue(validator.validate(line, "heat_network").isValid());
    }

    @Test
    void validPolygon() {
        Polygon polygon = gf.createPolygon(new Coordinate[]{
            new Coordinate(37.6, 55.7),
            new Coordinate(37.7, 55.7),
            new Coordinate(37.7, 55.8),
            new Coordinate(37.6, 55.8),
            new Coordinate(37.6, 55.7)
        });
        assertTrue(validator.validate(polygon, "oks_future").isValid());
    }

    @Test
    void nullGeometry() {
        assertFalse(validator.validate(null, "heat_network").isValid());
    }

    @Test
    void wrongType() {
        Point p = gf.createPoint(new Coordinate(37.6, 55.7));
        assertFalse(validator.validate(p, "heat_network").isValid());
    }

    @Test
    void coordinatesOutOfRange() {
        Point p = gf.createPoint(new Coordinate(200.0, 55.7));
        assertFalse(validator.validate(p, "heat_chamber").isValid());
    }
}