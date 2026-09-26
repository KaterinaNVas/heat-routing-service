package ru.lct.heatrouting.routing;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.HeatNetwork;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;
import ru.lct.heatrouting.network.GraphBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AStarRouterTest {

    private final GeometryFactory gf = new GeometryFactory();
    private final GraphBuilder builder = new GraphBuilder();
    private final AStarRouter router = new AStarRouter();

    @Test
    void findsShortestPath() {
        HeatNetwork net = new HeatNetwork();

        Node a = new Node("A", gf.createPoint(new Coordinate(37.60, 55.70)), "heat_chamber");
        Node b = new Node("B", gf.createPoint(new Coordinate(37.61, 55.71)), "heat_chamber");
        Node c = new Node("C", gf.createPoint(new Coordinate(37.62, 55.72)), "heat_chamber");

        net.addNode(a);
        net.addNode(b);
        net.addNode(c);
        net.addEdge(new Edge("AB", a, b, line(37.60, 55.70, 37.61, 55.71), 200, 0, "base"));
        net.addEdge(new Edge("BC", b, c, line(37.61, 55.71, 37.62, 55.72), 200, 0, "base"));
        net.addEdge(new Edge("AC", a, c, line(37.60, 55.70, 37.62, 55.72), 200, 0, "base"));

        Graph<Node, Edge> graph = builder.build(net);
        GraphPath<Node, Edge> path = router.findPath(graph, a, c);

        assertNotNull(path);
        assertEquals(a, path.getStartVertex());
        assertEquals(c, path.getEndVertex());
    }

    @Test
    void avoidsForbiddenZone() {
        HeatNetwork net = new HeatNetwork();

        Node a = new Node("A", gf.createPoint(new Coordinate(37.60, 55.70)), "heat_chamber");
        Node b = new Node("B", gf.createPoint(new Coordinate(37.60, 55.72)), "heat_chamber");
        Node c = new Node("C", gf.createPoint(new Coordinate(37.62, 55.72)), "heat_chamber");

        Edge ac = new Edge("AC", a, c, line(37.60, 55.70, 37.62, 55.72), 200, 0, "base");

        net.addNode(a);
        net.addNode(b);
        net.addNode(c);
        net.addEdge(ac);
        net.addEdge(new Edge("AB", a, b, line(37.60, 55.70, 37.60, 55.72), 200, 0, "base"));
        net.addEdge(new Edge("BC", b, c, line(37.60, 55.72, 37.62, 55.72), 200, 0, "base"));

        Polygon forbidden = gf.createPolygon(new Coordinate[]{
                new Coordinate(37.609, 55.709),
                new Coordinate(37.611, 55.709),
                new Coordinate(37.611, 55.711),
                new Coordinate(37.609, 55.711),
                new Coordinate(37.609, 55.709)
        });
        Restriction restriction = new Restriction(
                "R1",
                RestrictionType.PROHIBITED_SITE,
                "Test forbidden zone",
                forbidden
        );

        Graph<Node, Edge> graph = builder.build(net, List.of(restriction));
        GraphPath<Node, Edge> path = router.findPath(graph, a, c);

        assertNotNull(path);
        assertFalse(path.getEdgeList().contains(ac));
        assertEquals(3, path.getVertexList().size());
    }

    @Test
    void returnsNullWhenAllPathsForbidden() {
        HeatNetwork net = new HeatNetwork();

        Node a = new Node("A", gf.createPoint(new Coordinate(37.60, 55.70)), "heat_chamber");
        Node b = new Node("B", gf.createPoint(new Coordinate(37.61, 55.71)), "heat_chamber");
        Node c = new Node("C", gf.createPoint(new Coordinate(37.62, 55.72)), "heat_chamber");

        net.addNode(a);
        net.addNode(b);
        net.addNode(c);
        net.addEdge(new Edge("AB", a, b, line(37.60, 55.70, 37.61, 55.71), 200, 0, "base"));
        net.addEdge(new Edge("BC", b, c, line(37.61, 55.71, 37.62, 55.72), 200, 0, "base"));

        Polygon forbidden = gf.createPolygon(new Coordinate[]{
                new Coordinate(37.595, 55.695),
                new Coordinate(37.615, 55.695),
                new Coordinate(37.615, 55.715),
                new Coordinate(37.595, 55.715),
                new Coordinate(37.595, 55.695)
        });
        Restriction restriction = new Restriction(
                "R1",
                RestrictionType.WATER,
                "Test water zone",
                forbidden
        );

        Graph<Node, Edge> graph = builder.build(net, List.of(restriction));
        GraphPath<Node, Edge> path = router.findPath(graph, a, c);

        assertTrue(path == null || path.getEdgeList().isEmpty());
    }

    private LineString line(double x1, double y1, double x2, double y2) {
        return gf.createLineString(new Coordinate[]{
                new Coordinate(x1, y1),
                new Coordinate(x2, y2)
        });
    }
}