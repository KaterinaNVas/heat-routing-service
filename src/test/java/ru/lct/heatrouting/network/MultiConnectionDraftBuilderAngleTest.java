package ru.lct.heatrouting.network;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiConnectionDraftBuilderAngleTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test
    void rejectsSharpTurnBeforeBuildingCostTree() throws Exception {
        Point start = point(0, 0);
        Point tieIn = point(0, 1);
        LineString route = factory.createLineString(new Coordinate[]{
                new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(0, 1)});
        route.setSRID(32637);

        TerritoryRoutePlanner.Route planned = create(
                TerritoryRoutePlanner.Route.class,
                new Class<?>[]{LineString.class, String.class, Point.class},
                route, "pipe", tieIn);
        ConnectionResolver.Connection connection = create(
                ConnectionResolver.Connection.class,
                new Class<?>[]{String.class, Point.class, boolean.class, String.class},
                "chamber", tieIn, true, "pipe");
        MultiOksRoutePreparationService.PreparedConnection prepared = create(
                MultiOksRoutePreparationService.PreparedConnection.class,
                new Class<?>[]{ConnectionPoint.class, TerritoryRoutePlanner.Route.class,
                        ConnectionResolver.Connection.class, int.class},
                new ConnectionPoint("oks-sharp", 3.0, null, start), planned, connection, 100);
        MultiOksRoutePreparationService.Preparation input = create(
                MultiOksRoutePreparationService.Preparation.class,
                new Class<?>[]{List.class, List.class},
                List.of(List.of(prepared)), List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new MultiConnectionDraftBuilder().build(input));
        assertTrue(error.getMessage().contains("oks-sharp"));
        assertTrue(error.getMessage().contains("turn #1"));
    }

    private Point point(double x, double y) {
        Point point = factory.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    private static <T> T create(Class<T> type, Class<?>[] parameterTypes,
                                Object... arguments) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }
}
