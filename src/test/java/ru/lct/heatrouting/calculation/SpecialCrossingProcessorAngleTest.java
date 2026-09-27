package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialCrossingProcessorAngleTest {
    private final GeometryFactory factory = new GeometryFactory();
    private final SpecialCrossingProcessor processor = new SpecialCrossingProcessor();

    @Test
    void rejectsShallowCrossingOfRoadLine() {
        LineString route = line(-2, -1, 2, 1);
        LineString road = line(-3, 0, 3, 0);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> processor.validateAngles(route,
                        List.of(new Restriction("road-line", RestrictionType.ROAD, null, road))));
        assertTrue(error.getMessage().contains("road-line"));
    }

    @Test
    void checksEntryBoundaryOfRoadPolygon() {
        LineString route = line(-2, -2, 3, 6);
        Polygon road = factory.createPolygon(new Coordinate[]{
                new Coordinate(0, -10), new Coordinate(1, -10),
                new Coordinate(1, 10), new Coordinate(0, 10), new Coordinate(0, -10)});
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> processor.validateAngles(route,
                        List.of(new Restriction("road-polygon", RestrictionType.ROAD, null, road))));
        assertTrue(error.getMessage().contains("road-polygon"));
    }

    @Test
    void allowsPerpendicularRoadCrossing() {
        LineString route = line(-2, 0, 2, 0);
        Polygon road = factory.createPolygon(new Coordinate[]{
                new Coordinate(0, -2), new Coordinate(1, -2),
                new Coordinate(1, 2), new Coordinate(0, 2), new Coordinate(0, -2)});
        assertDoesNotThrow(() -> processor.validateAngles(route,
                List.of(new Restriction("road", RestrictionType.ROAD, null, road))));
    }

    private LineString line(double x1, double y1, double x2, double y2) {
        return factory.createLineString(new Coordinate[]{
                new Coordinate(x1, y1), new Coordinate(x2, y2)});
    }
}
