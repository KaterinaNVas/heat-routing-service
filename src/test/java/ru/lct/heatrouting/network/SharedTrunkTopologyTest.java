package ru.lct.heatrouting.network;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.lct.heatrouting.calculation.FlowPropagator;
import ru.lct.heatrouting.calculation.MultiConnectionCalculator;
import ru.lct.heatrouting.calculation.MultiTreeVariantCalculator;
import ru.lct.heatrouting.calculation.TopologyValidator;
import ru.lct.heatrouting.calculation.VariantCalculator;
import ru.lct.heatrouting.calculation.VariantSummary;
import ru.lct.heatrouting.model.ConnectionPoint;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.HeatChamber;
import ru.lct.heatrouting.model.HeatNetworkSegment;
import ru.lct.heatrouting.model.InputDataset;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.routing.TerritoryRoutePlanner;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedTrunkTopologyTest {
    private final GeometryFactory geometry = new GeometryFactory();

    @Test
    void buildsSingleSharedPipeWithSummedFlowAndJunctionChamber() {
        InputDataset dataset = new InputDataset();
        dataset.getHeatNetwork().add(new HeatNetworkSegment("existing", 100, null, null,
                line(new Coordinate(10, -1), new Coordinate(10, 1))));
        dataset.getHeatChambers().add(new HeatChamber("root", 100, null, point(10, 0)));
        dataset.getConnectionPoints().add(new ConnectionPoint("a", 2.0, null, point(0, -4)));
        dataset.getConnectionPoints().add(new ConnectionPoint("b", 3.0, null, point(0, 4)));

        TerritoryRoutePlanner planner = new TerritoryRoutePlanner() {
            @Override
            public Optional<Route> find(InputDataset input, ConnectionPoint oks, int diameter) {
                Coordinate start = oks.getGeometry().getCoordinate();
                LineString route = line(start, new Coordinate(5, 0), new Coordinate(10, 0));
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
        List<MultiConnectionDraftBuilder.Draft> drafts = new MultiConnectionDraftBuilder().build(prepared);
        assertEquals(1, drafts.size());
        MultiConnectionDraftBuilder.Draft tree = drafts.get(0);
        assertEquals(3, tree.getParentEdge().size());
        Edge trunk = tree.getParentEdge().values().stream()
                .filter(edge -> "root".equals(edge.getTo().getId()))
                .findFirst().orElseThrow();
        Node junction = trunk.getFrom();
        assertEquals("heat_chamber", junction.getObjectType());
        assertEquals(2, tree.getParentEdge().values().stream()
                .filter(edge -> edge.getTo().equals(junction)).count());
        VariantCalculator pricing = new VariantCalculator(0.7, 0.3);
        VariantSummary summary = new MultiTreeVariantCalculator(
                new MultiConnectionCalculator(new FlowPropagator(), pricing,
                        new TopologyValidator()), pricing).calculateVariant(
                "v1", drafts, List.of(), List.of());
        assertEquals(5.0, summary.getFlowsByEdgeId().get(trunk.getId()), 1e-9);
        assertEquals(3, summary.getSegments().size());
        assertEquals(1, summary.getExistingChamberTieInCount());
        assertTrue(summary.getChamberConstructionCost() > 0);
    }

    private Point point(double x, double y) {
        Point point = geometry.createPoint(new Coordinate(x, y));
        point.setSRID(32637);
        return point;
    }

    private LineString line(Coordinate... coordinates) {
        LineString route = geometry.createLineString(coordinates);
        route.setSRID(32637);
        return route;
    }
}
