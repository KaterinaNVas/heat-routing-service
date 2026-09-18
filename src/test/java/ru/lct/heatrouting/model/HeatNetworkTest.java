package ru.lct.heatrouting.model;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class HeatNetworkTest {

    private final GeometryFactory gf = new GeometryFactory();

    @Test
    void addNodesAndEdges() {
        HeatNetwork network = new HeatNetwork();

        Node a = new Node("A",
            gf.createPoint(new Coordinate(37.6, 55.7)), "heat_chamber");
        Node b = new Node("B",
            gf.createPoint(new Coordinate(37.61, 55.71)), "heat_chamber");

        LineString geometry = gf.createLineString(new Coordinate[]{
            new Coordinate(37.6, 55.7),
            new Coordinate(37.61, 55.71)
        });

        Edge edge = new Edge("E1", a, b, geometry, 200, 50.0, "base");

        network.addNode(a);
        network.addNode(b);
        network.addEdge(edge);

        assertEquals(2, network.getNodeCount());
        assertEquals(1, network.getEdgeCount());
        assertEquals(200, network.getEdges().get(0).getDiameter());
    }

    @Test
    void nodeEqualsById() {
        Point p1 = gf.createPoint(new Coordinate(37.6, 55.7));
        Point p2 = gf.createPoint(new Coordinate(38.0, 56.0));

        Node a1 = new Node("A", p1, "heat_chamber");
        Node a2 = new Node("A", p2, "heat_chamber");  // тот же ID, другая точка
        Node b  = new Node("B", p1, "heat_chamber");

        assertEquals(a1, a2);         // равны по ID
        assertNotEquals(a1, b);       // разные ID
    }
}