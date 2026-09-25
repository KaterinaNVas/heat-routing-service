package ru.lct.heatrouting.calculation;

import org.springframework.stereotype.Component;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.cost.SegmentCostCalculator;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;
import ru.lct.heatrouting.network.NewSegment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class MultiConnectionCalculator {

    private final FlowPropagator flowPropagator;
    private final VariantCalculator variantCalculator;
    private final DiameterCatalog catalog = new DiameterCatalog();
    private final SegmentCostCalculator segmentCosts = new SegmentCostCalculator(catalog);

    public MultiConnectionCalculator(FlowPropagator flowPropagator,
                                     VariantCalculator variantCalculator) {
        this.flowPropagator = Objects.requireNonNull(flowPropagator);
        this.variantCalculator = Objects.requireNonNull(variantCalculator);
    }

    public VariantSummary calculate(String variantId,
                                    Node root,
                                    Map<Node, Edge> parentEdge,
                                    Map<Node, Double> demands,
                                    boolean isExistingChamber,
                                    List<Object> unconnectedOksIds,
                                    Collection<Double> unconnectedFlows) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(parentEdge, "parentEdge");
        Objects.requireNonNull(demands, "demands");
        if (parentEdge.isEmpty()) {
            throw new IllegalArgumentException("Нет ни одного нового участка");
        }

        Map<String, Double> flows = flowPropagator.propagate(root, parentEdge, demands);

        Map<String, NewSegment> segmentsByEdgeId = new HashMap<>();
        Map<String, Integer> diametersByEdgeId = new HashMap<>();
        int maxDiameter = 0;
        double totalLength = 0.0;

        for (Map.Entry<Node, Edge> entry : parentEdge.entrySet()) {
            Edge edge = entry.getValue();
            double flow = flows.get(edge.getId());
            double length = edge.getLengthMeters();
            int dn = catalog.findMinimalDiameter(flow, length);

            NewSegment segment = new NewSegment(edge.getId(), dn, length);
            segmentsByEdgeId.put(edge.getId(), segment);
            diametersByEdgeId.put(edge.getId(), dn);
            maxDiameter = Math.max(maxDiameter, dn);
            totalLength += length;
        }

        for (Node oks : demands.keySet()) {
            List<NewSegment> path = collectPath(root, oks, parentEdge, segmentsByEdgeId);
            if (!segmentCosts.checkMaxLength(path)) {
                throw new IllegalStateException(
                        "Превышена предельная длина на пути от ОКС " + oks.getId());
            }
        }

        for (Node oks : demands.keySet()) {
            checkDiameterMonotonic(root, oks, parentEdge, diametersByEdgeId);
        }

        double segmentCost = segmentsByEdgeId.values().stream()
                .mapToDouble(segmentCosts::calculateNewSegmentCost)
                .sum();

        double chamberConstructionCost = isExistingChamber
                ? 0.0
                : variantCalculator.chamberCost(maxDiameter);
        int tieIns = isExistingChamber ? 1 : 0;
        double tieInCost = 5_000_000.0 * tieIns;

        double penalty = unconnectedFlows == null || unconnectedFlows.isEmpty()
                ? 0.0
                : variantCalculator.unconnectedPenalty(unconnectedFlows);

        double constructionCost = variantCalculator.constructionCost(
                segmentCost, chamberConstructionCost, tieIns);
        double calculatedCost = constructionCost + penalty;
        double score = variantCalculator.score(calculatedCost, totalLength);

        List<NewSegment> allSegments = new ArrayList<>(segmentsByEdgeId.values());

        return new VariantSummary(
                variantId,
                1,
                allSegments,
                flows,
                constructionCost,
                chamberConstructionCost,
                tieIns,
                tieInCost,
                penalty,
                calculatedCost,
                totalLength,
                score,
                unconnectedOksIds
        );
    }

    private List<NewSegment> collectPath(Node root, Node oks,
                                         Map<Node, Edge> parentEdge,
                                         Map<String, NewSegment> segmentsByEdgeId) {
        List<NewSegment> path = new ArrayList<>();
        Node current = oks;
        while (!current.equals(root)) {
            Edge edge = parentEdge.get(current);
            if (edge == null) {
                throw new IllegalStateException(
                        "Нет пути к корню от узла " + current.getId());
            }
            NewSegment segment = segmentsByEdgeId.get(edge.getId());
            if (segment == null) {
                throw new IllegalStateException(
                        "Нет сегмента для ребра " + edge.getId());
            }
            path.add(segment);
            current = edge.getTo();
        }
        return path;
    }

    private void checkDiameterMonotonic(Node root, Node oks,
                                        Map<Node, Edge> parentEdge,
                                        Map<String, Integer> diametersByEdgeId) {
        Node current = oks;
        int previousDn = -1;
        while (!current.equals(root)) {
            Edge edge = parentEdge.get(current);
            if (edge == null) {
                throw new IllegalStateException(
                        "Нет пути к корню от узла " + current.getId());
            }
            int dn = diametersByEdgeId.get(edge.getId());
            if (previousDn >= 0 && dn < previousDn) {
                throw new IllegalStateException(
                        "ДУ уменьшается по направлению к корню: "
                                + previousDn + " -> " + dn
                                + " (ребро " + edge.getId() + ")");
            }
            previousDn = dn;
            current = edge.getTo();
        }
    }
}
