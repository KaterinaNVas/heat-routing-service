package ru.lct.heatrouting.network;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.calculation.FlowPropagator;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionResolverTest {
    private final GeometryFactory factory = new GeometryFactory();

    private Point point(double x, double y) {
        Point point = factory.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    private InputDataset dataset() {
        InputDataset dataset = new InputDataset();
        LineString line = factory.createLineString(new Coordinate[]{new Coordinate(100, -20),
                new Coordinate(100, 20)});
        line.setSRID(32637);
        dataset.getHeatNetwork().add(new HeatNetworkSegment("existing", 100, null, null, line));
        return dataset;
    }

    @Test
    void createsChamberAndDraftUsableByFlowPropagator() {
        InputDataset dataset = dataset();
        ConnectionPoint oks = new ConnectionPoint("oks", 3.5, null, point(0, 0));
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(dataset, oks, 50).orElseThrow();
        ConnectionResolver.Connection connection = new ConnectionResolver().resolve(dataset, route, "new-chamber");
        assertFalse(connection.isExistingChamber());
        SingleConnectionDraftBuilder.Draft draft = new SingleConnectionDraftBuilder()
                .build(oks, route, connection, "new-segment", 50);
        assertEquals(3.5, new FlowPropagator().propagate(draft.getRoot(),
                draft.getParentEdge(), draft.getDemands()).get("new-segment"));
    }

    @Test
    void choosesExistingChamberWithinTenMeters() {
        InputDataset dataset = dataset();
        dataset.getHeatChambers().add(new HeatChamber("old", 100, null, point(100, 9)));
        ConnectionPoint oks = new ConnectionPoint("oks", 2.0, null, point(0, 0));
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(dataset, oks, 50).orElseThrow();
        ConnectionResolver.Connection connection = new ConnectionResolver().resolve(dataset, route, "new");
        assertTrue(connection.isExistingChamber());
        assertEquals("old", connection.getChamberId());
        assertThrows(IllegalArgumentException.class,
                () -> new SingleConnectionDraftBuilder().build(oks, route, connection, "segment", 50));
    }

    @Test
    void fullChamberRequiresNewChamberAtChosenPoint() {
        InputDataset dataset = dataset();
        Point p = point(100, 0);
        dataset.getHeatChambers().add(new HeatChamber("full", 100, null, p));
        // Два проходящих через камеру объекта дают четыре существующих примыкания.
        LineString other = factory.createLineString(new Coordinate[]{new Coordinate(90, 0),
                new Coordinate(110, 0)});
        other.setSRID(32637);
        dataset.getHeatNetwork().add(new HeatNetworkSegment("cross", 100, null, null, other));
        ConnectionPoint oks = new ConnectionPoint("oks", 2.0, null, point(0, 0));
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(dataset, oks, 50).orElseThrow();
        ConnectionResolver.Connection connection = new ConnectionResolver().resolve(dataset, route, "new");
        assertFalse(connection.isExistingChamber());
    }
}
