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
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.lang.reflect.Constructor;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedTrunkChamberCapacityTest {
    private final GeometryFactory geometry = new GeometryFactory();

    @Test
    void marksExcessConnectionsUnconnectedWhenNoOtherChamberExists() {
        InputDataset dataset = new InputDataset();
        dataset.getHeatNetwork().add(new HeatNetworkSegment("existing", 100, null, null,
                line(new Coordinate(10, -1), new Coordinate(10, 1))));
        dataset.getHeatChambers().add(new HeatChamber("root", 100, null, point(10, 0)));
        double[] y = {-4, -2, 2, 4};
        for (int i = 0; i < y.length; i++) {
            dataset.getConnectionPoints().add(new ConnectionPoint("oks-" + i, 1.0,
                    null, point(0, y[i])));
        }
        TerritoryRoutePlanner planner = new TerritoryRoutePlanner() {
            @Override public Optional<Route> find(InputDataset ignored, ConnectionPoint oks, int diameter) {
                LineString route = line(oks.getGeometry().getCoordinate(),
                        new Coordinate(5, 0), new Coordinate(10, 0));
                try {
                    Constructor<Route> ctor = Route.class.getDeclaredConstructor(
                            LineString.class, String.class, Point.class);
                    ctor.setAccessible(true);
                    return Optional.of(ctor.newInstance(route, "existing", point(10, 0)));
                } catch (ReflectiveOperationException exception) {
                    throw new AssertionError(exception);
                }
            }
        };
        MultiOksRoutePreparationService.Preparation prepared =
                new MultiOksRoutePreparationService(planner, new ConnectionResolver(),
                        new OksConnectionGrouper()).prepare(dataset);
        assertEquals(2, prepared.getUnconnectedOksIds().size());
        assertEquals(2, prepared.getGroups().get(0).size());
        new MultiConnectionDraftBuilder().build(prepared);
    }

    private Point point(double x, double y) {
        Point point = geometry.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    private LineString line(Coordinate... coordinates) {
        LineString line = geometry.createLineString(coordinates);
        line.setSRID(32637);
        return line;
    }
}
