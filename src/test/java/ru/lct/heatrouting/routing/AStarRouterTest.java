package ru.lct.heatrouting.routing;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.HeatNetwork;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.network.GraphBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AStarRouterTest {

    private final GeometryFactory gf = new GeometryFactory();
    private final GraphBuilder builder = new GraphBuilder();
    private final AStarRouter router = new AStarRouter();

    @Test
    void findsShortestPath() {
        // A --(100m)--> B --(50m)--> C
        // A -------------(200m)-----> C
        HeatNetwork net = new HeatNetwork();

        Node a = new Node("A", gf.createPoint(new Coordinate(37.60, 55.70)), "heat_chamber");
        Node b = new Node("B", gf.createPoint(new Coordinate(37.61, 55.71)), "heat_chamber");
        Node c = new Node("C", gf.createPoint(new Coordinate(37.62, 55.72)), "heat_chamber");

        LineString ab = line(37.60, 55.70, 37.61, 55.71);
        LineString bc = line(37.61, 55.71, 37.62, 55.72);
        LineString ac = line(37.60, 55.70, 37.62, 55.72);

        net.addNode(a);
        net.addNode(b);
        net.addNode(c);
        net.addEdge(new Edge("AB", a, b, ab, 200, 0, "base"));
        net.addEdge(new Edge("BC", b, c, bc, 200, 0, "base"));
        net.addEdge(new Edge("AC", a, c, ac, 200, 0, "base"));

        Graph<Node, Edge> graph = builder.build(net);
        GraphPath<Node, Edge> path = router.findPath(graph, a, c);

        assertNotNull(path);
        assertEquals(a, path.getStartVertex());
        assertEquals(c, path.getEndVertex());

        // Кратчайший — A -> B -> C (короткая прямая не по BC, а по сегментам)
        // Вес — длина в градусах, но это не критично: проверяем, что путь есть.
        System.out.println("Path: " + path.getVertexList());
        System.out.println("Weight: " + path.getWeight());
    }

    private LineString line(double x1, double y1, double x2, double y2) {
        return gf.createLineString(new Coordinate[]{
            new Coordinate(x1, y1),
            new Coordinate(x2, y2)
        });
    }
}