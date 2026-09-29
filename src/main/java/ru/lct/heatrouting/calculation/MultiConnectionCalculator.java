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
    private final TopologyValidator validator;
    private final DiameterCatalog catalog = new DiameterCatalog();
    private final SegmentCostCalculator segmentCosts = new SegmentCostCalculator(catalog);

    public MultiConnectionCalculator(FlowPropagator flowPropagator,
                                     VariantCalculator variantCalculator,
                                     TopologyValidator validator) {
        this.flowPropagator = Objects.requireNonNull(flowPropagator, "flowPropagator");
        this.variantCalculator = Objects.requireNonNull(variantCalculator, "variantCalculator");
        this.validator = Objects.requireNonNull(validator, "validator");
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

        // 1. Валидация топологии
        validator.validateBranchingsInChambers(parentEdge);

        // 2. Расчёт расходов
        Map<String, Double> flows = flowPropagator.propagate(root, parentEdge, demands);

        // 3. Подбор ДУ для участков с ненулевым расходом
        Map<String, NewSegment> segmentsByEdgeId = new HashMap<>();
        Map<String, Integer> diametersByEdgeId = new HashMap<>();
        int maxDiameter = 0;
        double totalLength = 0.0;

        for (Map.Entry<Node, Edge> entry : parentEdge.entrySet()) {
            Edge edge = entry.getValue();
            double flow = flows.get(edge.getId());

            // Участки с нулевым расходом не включаем в строительство (ТЗ, разъяснение)
            if (flow <= 0.0) {
                continue;
            }

            double length = edge.getLengthMeters();
            int dn = catalog.findMinimalDiameter(flow, length);

            NewSegment segment = new NewSegment(edge.getId(), dn, length);
            segmentsByEdgeId.put(edge.getId(), segment);
            diametersByEdgeId.put(edge.getId(), dn);
            maxDiameter = Math.max(maxDiameter, dn);
            totalLength += length;
        }

        // Equal-flow consecutive edges form one hydraulic section. Size the
        // whole section for its total length, rather than resetting the limit
        // at each route vertex or choosing a larger DN on only one short edge.
        for (Node oks : demands.keySet()) {
            Node current = oks;
            while (!current.equals(root)) {
                Edge first = parentEdge.get(current);
                if (first == null) throw new IllegalStateException("Нет пути к корню от " + oks.getId());
                double flow = flows.get(first.getId());
                if (flow <= 0) {
                    current = first.getTo();
                    continue;
                }
                List<Edge> section = new ArrayList<>();
                double sectionLength = 0;
                while (!current.equals(root)) {
                    Edge edge = parentEdge.get(current);
                    if (edge == null) throw new IllegalStateException("Нет пути к корню от " + oks.getId());
                    if (Double.compare(flows.get(edge.getId()), flow) != 0) break;
                    section.add(edge);
                    sectionLength += edge.getLengthMeters();
                    current = edge.getTo();
                }
                int sectionDn = catalog.findMinimalDiameter(flow, sectionLength);
                for (Edge edge : section) {
                    int dn = Math.max(diametersByEdgeId.get(edge.getId()), sectionDn);
                    diametersByEdgeId.put(edge.getId(), dn);
                    segmentsByEdgeId.put(edge.getId(),
                            new NewSegment(edge.getId(), dn, edge.getLengthMeters()));
                    maxDiameter = Math.max(maxDiameter, dn);
                }
            }
        }

        // A larger downstream pipe must not shrink on its way to the tie-in.
        for (Node oks : demands.keySet()) {
            Node current = oks;
            int downstreamDn = 0;
            while (!current.equals(root)) {
                Edge edge = parentEdge.get(current);
                if (edge == null) throw new IllegalStateException("Нет пути к корню от " + oks.getId());
                Integer selected = diametersByEdgeId.get(edge.getId());
                if (selected != null) {
                    int dn = Math.max(selected, downstreamDn);
                    diametersByEdgeId.put(edge.getId(), dn);
                    segmentsByEdgeId.put(edge.getId(),
                            new NewSegment(edge.getId(), dn, edge.getLengthMeters()));
                    maxDiameter = Math.max(maxDiameter, dn);
                    downstreamDn = dn;
                }
                current = edge.getTo();
            }
        }

        // 4. Проверка монотонности подобранного ДУ
        validator.validatePickedDiameterMonotonicity(
            root, parentEdge, demands, diametersByEdgeId);

        // 5. Стоимость
        double segmentCost = segmentsByEdgeId.values().stream()
            .mapToDouble(segmentCosts::calculateNewSegmentCost)
            .sum();

        double chamberConstructionCost = isExistingChamber
            ? 0.0
            : variantCalculator.chamberCost(maxDiameter);
        int tieIns = isExistingChamber ? (int) parentEdge.values().stream()
                .filter(edge -> segmentsByEdgeId.containsKey(edge.getId()))
                .filter(edge -> root.equals(edge.getTo())).count() : 0;
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

}
