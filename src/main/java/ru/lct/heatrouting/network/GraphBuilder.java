package ru.lct.heatrouting.network;

import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.HeatNetwork;
import ru.lct.heatrouting.model.Node;

/**
 * Строит JGraphT-граф из HeatNetwork.
 * Вершины — Node, рёбра — Edge. Вес ребра — длина в метрах.
 */
@Component
public class GraphBuilder {

    public Graph<Node, Edge> build(HeatNetwork network) {
        Graph<Node, Edge> graph =
            new SimpleDirectedWeightedGraph<>(Edge.class);

        for (Node node : network.getNodes()) {
            graph.addVertex(node);
        }

        for (Edge edge : network.getEdges()) {
            Node from = edge.getFrom();
            Node to = edge.getTo();

            if (!graph.containsVertex(from) || !graph.containsVertex(to)) {
                throw new IllegalArgumentException(
                    "Edge " + edge.getId() + " references unknown node");
            }

            graph.addEdge(from, to, edge);
            graph.setEdgeWeight(edge, edge.getLengthMeters());
        }

        return graph;
    }
}