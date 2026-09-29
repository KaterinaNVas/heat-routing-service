package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpecialCrossingProcessorTest {

    private final GeometryFactory factory = new GeometryFactory();
    private final SpecialCrossingProcessor processor = new SpecialCrossingProcessor();

    private LineString route(double x1, double y1, double x2, double y2) {
        return factory.createLineString(new Coordinate[]{
            new Coordinate(x1, y1), new Coordinate(x2, y2)
        });
    }

    private Polygon polygon(double x1, double y1, double x2, double y2) {
        return factory.createPolygon(new Coordinate[]{
            new Coordinate(x1, y1), new Coordinate(x2, y1),
            new Coordinate(x2, y2), new Coordinate(x1, y2),
            new Coordinate(x1, y1)
        });
    }

    @Test
    void acceptsRouteWithNoCrossings() {
        LineString route = route(0, 0, 10, 0);
        Restriction road = new Restriction("r1", RestrictionType.ROAD, null,
            polygon(20, 20, 25, 25));

        assertDoesNotThrow(() ->
            processor.validateNoForbiddenCrossings(route, List.of(road)));
    }

    @Test
    void rejectsRouteCrossingForbiddenZone() {
        LineString route = route(0, 0, 10, 0);
        Restriction park = new Restriction("r1", RestrictionType.PARK, null,
            polygon(4, -1, 6, 1));  // маршрут проходит через

        assertThrows(IllegalStateException.class,
            () -> processor.validateNoForbiddenCrossings(route, List.of(park)));
    }

    @Test
    void rejectsRailwayCrossing() {
        Restriction railway = new Restriction("rail", RestrictionType.RAILWAY, null,
                polygon(4, -1, 6, 1));
        assertThrows(IllegalStateException.class, () ->
                processor.validateNoForbiddenCrossings(route(0, 0, 10, 0), List.of(railway)));
    }

    @Test
    void acceptsRouteCrossingAllowedZone() {
        LineString route = route(0, 0, 10, 0);
        Restriction road = new Restriction("r1", RestrictionType.ROAD, null,
            polygon(4, -1, 6, 1));

        assertDoesNotThrow(() ->
            processor.validateNoForbiddenCrossings(route, List.of(road)));
    }

    @Test
    void returnsMaxKSpecForSingleCrossing() {
        LineString route = route(0, 0, 10, 0);
        Restriction road = new Restriction("r1", RestrictionType.ROAD, null,
            polygon(4, -1, 6, 1));

        assertEquals(1.60, processor.findMaxKSpec(route, List.of(road)), 1e-9);
    }

    @Test
    void returnsMaxKSpecForMultipleOverlappingCrossings() {
        LineString route = route(0, 0, 10, 0);
        Restriction road = new Restriction("r1", RestrictionType.ROAD, null,
            polygon(2, -1, 4, 1));                 // Кспец 1.60
        Restriction tram = new Restriction("r2", RestrictionType.TRAM_TRACKS, null,
            polygon(4, -1, 6, 1));                 // Кспец 1.75

        // Должен вернуться максимум — 1.75, не 1.60 × 1.75 = 2.80
        assertEquals(1.75,
            processor.findMaxKSpec(route, List.of(road, tram)), 1e-9);
    }

    @Test
    void returnsDefaultKSpecForNoCrossings() {
        LineString route = route(0, 0, 10, 0);
        Restriction road = new Restriction("r1", RestrictionType.ROAD, null,
            polygon(20, 20, 25, 25));

        assertEquals(1.0, processor.findMaxKSpec(route, List.of(road)), 1e-9);
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> processor.validateNoForbiddenCrossings(null, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> processor.validateNoForbiddenCrossings(route(0, 0, 1, 1), null));
    }
}
