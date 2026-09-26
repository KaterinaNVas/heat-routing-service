package ru.lct.heatrouting.network;

import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.HeatNetwork;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.model.Restriction;
import ru.lct.heatrouting.model.RestrictionType;

import java.util.List;
import java.util.Set;

/**
 * Строит JGraphT-граф из HeatNetwork.
 * <p>
 * Вес ребра — длина в метрах.
 * <p>
 * Рёбра, пересекающие запретные зоны (PROHIBITED_SITE, WATER),
 * в граф не добавляются.
 */
@Component
public class GraphBuilder {

    /** Типы запретов, при пересечении с которыми ребро отбрасывается. */
    private static final Set<RestrictionType> FORBIDDEN_TYPES = Set.of(
            RestrictionType.PROHIBITED_SITE,
            RestrictionType.WATER
    );

    public Graph<Node, Edge> build(HeatNetwork network) {
        return build(network, List.of());
    }

    public Graph<Node, Edge> build(HeatNetwork network,
                                   List<Restriction> restrictions) {
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

            // Пропускаем ребро, если оно пересекает запретную зону
            if (crossesForbiddenZone(edge, restrictions)) {
                continue;
            }

            graph.addEdge(from, to, edge);
            graph.setEdgeWeight(edge, edge.getLengthMeters());
        }

        return graph;
    }

    private boolean crossesForbiddenZone(Edge edge,
                                         List<Restriction> restrictions) {
        if (restrictions == null || restrictions.isEmpty()) {
            return false;
        }

        Geometry edgeGeometry = edge.getGeometry();

        for (Restriction restriction : restrictions) {
            if (restriction == null) continue;
            if (!FORBIDDEN_TYPES.contains(restriction.getRestrictionType())) {
                continue;
            }
            Geometry zone = restriction.getGeometry();
            if (zone == null) continue;

            if (edgeGeometry.intersects(zone)) {
                return true;
            }
        }
        return false;
    }
}