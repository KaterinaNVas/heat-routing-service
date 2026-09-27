package ru.lct.heatrouting.network;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiOksCrossingRejectionTest {
    private final GeometryFactory factory = new GeometryFactory();

    @Test
    void invalidCrossingDoesNotDiscardOtherOks() {
        InputDataset metric = new InputDataset();
        metric.getHeatNetwork().add(new HeatNetworkSegment("pipe", 100, null, null,
                line(2, -1, 2, 4)));
        metric.getHeatChambers().add(new HeatChamber("chamber-1", 100, null, point(2, 1)));
        metric.getHeatChambers().add(new HeatChamber("chamber-2", 100, null, point(2, 3)));
        metric.getConnectionPoints().add(new ConnectionPoint("shallow", 5.0, null, point(-2, -1)));
        metric.getConnectionPoints().add(new ConnectionPoint("good", 5.0, null, point(-2, 3)));
        metric.getRestrictions().add(new Restriction("road", RestrictionType.ROAD,
                null, line(-3, 0, 3, 0)));

        TerritoryRoutePlanner planner = new TerritoryRoutePlanner() {
            @Override
            public Optional<Route> find(InputDataset dataset, ConnectionPoint oks, int diameter) {
                Point end = "shallow".equals(oks.getId()) ? point(2, 1) : point(2, 3);
                LineString geometry = line(oks.getGeometry().getX(), oks.getGeometry().getY(),
                        end.getX(), end.getY());
                try {
                    Constructor<Route> constructor = Route.class.getDeclaredConstructor(
                            LineString.class, String.class, Point.class);
                    constructor.setAccessible(true);
                    return Optional.of(constructor.newInstance(geometry, "pipe", end));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }
        };

        MultiOksRoutePreparationService.Preparation prepared =
                new MultiOksRoutePreparationService(planner, new ConnectionResolver(),
                        new OksConnectionGrouper()).prepare(metric);
        assertEquals(List.of("shallow"), prepared.getUnconnectedOksIds());
        assertEquals(1, prepared.getGroups().size());
        assertEquals("good", prepared.getGroups().get(0).get(0).getOks().getId());
    }

    private Point point(double x, double y) {
        Point point = factory.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    private LineString line(double x1, double y1, double x2, double y2) {
        LineString line = factory.createLineString(new Coordinate[]{
                new Coordinate(x1, y1), new Coordinate(x2, y2)});
        line.setSRID(32637);
        return line;
    }
}
