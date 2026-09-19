package ru.lct.heatrouting.routing;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AStarShortestPath;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

/**
 * A* поиск пути на графе тепловой сети.
 * <p>
 * Эвристика — расстояние по прямой между точками (JTS distance).
 */
@Component
public class AStarRouter {

    public GraphPath<Node, Edge> findPath(Graph<Node, Edge> graph,
                                          Node from,
                                          Node to) {
        AStarShortestPath<Node, Edge> aStar =
            new AStarShortestPath<>(graph, (source, target) ->
                source.getPoint().distance(target.getPoint()));

        return aStar.getPath(from, to);
    }
}