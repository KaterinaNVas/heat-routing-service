package ru.lct.heatrouting.importdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import ru.lct.heatrouting.geo.CoordinateTransformService;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoJsonGeometryReaderTest {
    @Test
    void polygonFromInputCanBeBufferedAfterCoordinateTransform() throws Exception {
        Geometry polygon = new GeoJsonGeometryReader().read(new ObjectMapper().readTree(
                "{\"type\":\"Polygon\",\"coordinates\":[[[37.6,55.75],[37.601,55.75],"
                        + "[37.601,55.751],[37.6,55.751],[37.6,55.75]]]}"));
        Geometry metric = new CoordinateTransformService().toMetric(polygon);
        assertTrue(metric.buffer(1.0).getArea() > metric.getArea());
    }
}
