package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.network.ConnectionResolver;
import ru.lct.heatrouting.network.SingleConnectionDraftBuilder;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleConnectionCalculatorTest {
    private SingleConnectionDraftBuilder.Draft draft(double flow) {
        GeometryFactory factory = new GeometryFactory();
        Point oksPoint = factory.createPoint(new Coordinate(0, 0));
        oksPoint.setSRID(32637);
        ConnectionPoint oks = new ConnectionPoint("oks", flow, null, oksPoint);
        LineString existing = factory.createLineString(new Coordinate[]{
                new Coordinate(100, -10), new Coordinate(100, 10)});
        existing.setSRID(32637);
        InputDataset dataset = new InputDataset();
        dataset.getHeatNetwork().add(new HeatNetworkSegment("old", 100, null, null, existing));
        TerritoryRoutePlanner.Route route = new TerritoryRoutePlanner().find(dataset, oks, 50).orElseThrow();
        ConnectionResolver.Connection connection = new ConnectionResolver()
                .resolve(dataset, route, "new-chamber");
        return new SingleConnectionDraftBuilder().build(oks, route, connection, "new-edge", 50);
    }

    @Test
    void calculatesNewSegmentAndChamber() {
        SingleConnectionCalculator calculator = new SingleConnectionCalculator(
                new FlowPropagator(), new VariantCalculator(0.7, 0.3));
        SingleConnectionCalculator.CalculatedConnection result = calculator.calculate(draft(2));
        assertEquals(2, result.getEdge().getFlowTph(), 1e-9);
        assertEquals(50, result.getEdge().getDiameter());
        assertEquals(7_402_300, result.getSegmentCost(), 1e-6);
        assertEquals(3_000_000, result.getChamberCost(), 1e-6);
        assertEquals(10_402_300, result.getConstructionCost(), 1e-6);
        assertEquals(0.7 * 10_402_300 / 25_000_000 + 0.3, result.getScore(), 1e-9);
    }

    @Test
    void demandsRerouteWhenDiameterChanges() {
        SingleConnectionCalculator calculator = new SingleConnectionCalculator(
                new FlowPropagator(), new VariantCalculator(0.7, 0.3));
        SingleConnectionCalculator.DiameterChangeRequiredException error = assertThrows(
                SingleConnectionCalculator.DiameterChangeRequiredException.class,
                () -> calculator.calculate(draft(20)));
        assertEquals(100, error.getRequiredDiameter());
    }
}
