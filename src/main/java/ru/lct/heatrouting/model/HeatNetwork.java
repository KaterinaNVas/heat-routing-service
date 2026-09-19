package ru.lct.heatrouting.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Вся тепловая сеть — коллекция узлов и рёбер.
 */
public class HeatNetwork {

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    public void addNode(Node node) {
        nodes.add(node);
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<Edge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    @Override
    public String toString() {
        return "HeatNetwork{nodes=" + nodes.size()
            + ", edges=" + edges.size() + "}";
    }
}