package ru.lct.heatrouting.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateTransformServiceTest {

    private CoordinateTransformService service;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        service = new CoordinateTransformService();
        geometryFactory = new GeometryFactory();
    }

    @Test
    void shouldTransformWgs84PointToMetricCrs() {
        Point point = geometryFactory.createPoint(
                new Coordinate(37.6176, 55.7558)
        );

        point.setSRID(4326);

        Geometry transformed = service.toMetric(point);

        assertEquals(32637, transformed.getSRID());

        Coordinate coordinate =
                transformed.getCoordinate();

        assertTrue(
                coordinate.x > 300000
                        && coordinate.x < 500000
        );

        assertTrue(
                coordinate.y > 6000000
                        && coordinate.y < 6300000
        );
    }

    @Test
    void shouldTransformBackToWgs84() {
        Point original = geometryFactory.createPoint(
                new Coordinate(37.6176, 55.7558)
        );

        original.setSRID(4326);

        Geometry metric = service.toMetric(original);
        Geometry restored = service.toWgs84(metric);

        assertEquals(4326, restored.getSRID());

        Coordinate restoredCoordinate =
                restored.getCoordinate();

        assertEquals(
                37.6176,
                restoredCoordinate.x,
                0.00001
        );

        assertEquals(
                55.7558,
                restoredCoordinate.y,
                0.00001
        );
    }

    @Test
    void shouldNotModifyOriginalGeometry() {
        Point original = geometryFactory.createPoint(
                new Coordinate(37.6176, 55.7558)
        );

        original.setSRID(4326);

        service.toMetric(original);

        assertEquals(
                37.6176,
                original.getX(),
                0.0000001
        );

        assertEquals(
                55.7558,
                original.getY(),
                0.0000001
        );

        assertEquals(4326, original.getSRID());
    }
}