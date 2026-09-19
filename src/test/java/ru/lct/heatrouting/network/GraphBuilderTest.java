package ru.lct.heatrouting.network;

import org.jgrapht.Graph;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.HeatNetwork;
import ru.lct.heatrouting.model.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphBuilderTest {

    private final GeometryFactory gf = new GeometryFactory();
    private final GraphBuilder builder = new GraphBuilder();

    @Test
    void buildGraphFromNetwork() {
        HeatNetwork network = new HeatNetwork();

        Node a = new Node("A",
            gf.createPoint(new Coordinate(37.6, 55.7)), "heat_chamber");
        Node b = new Node("B",
            gf.createPoint(new Coordinate(37.61, 55.71)), "heat_chamber");

        LineString line = gf.createLineString(new Coordinate[]{
            new Coordinate(37.6, 55.7),
            new Coordinate(37.61, 55.71)
        });
        Edge e = new Edge("E1", a, b, line, 200, 50.0, "base");

        network.addNode(a);
        network.addNode(b);
        network.addEdge(e);

        Graph<Node, Edge> graph = builder.build(network);

        assertEquals(2, graph.vertexSet().size());
        assertEquals(1, graph.edgeSet().size());
        assertTrue(graph.containsEdge(a, b));

        // Проверяем вес ребра — длина линии
        assertEquals(line.getLength(),
            graph.getEdgeWeight(e), 0.0001);
    }
}