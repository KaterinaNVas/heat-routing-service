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

        // 4. Проверка предельной длины для каждого пути ОКС
        for (Node oks : demands.keySet()) {
            List<NewSegment> path = collectPath(root, oks, parentEdge, segmentsByEdgeId);
            if (!segmentCosts.checkMaxLength(path)) {
                throw new IllegalStateException(
                    "Превышена предельная длина на пути от ОКС " + oks.getId());
            }
        }

        // 5. Проверка монотонности подобранного ДУ
        validator.validatePickedDiameterMonotonicity(
            root, parentEdge, demands, diametersByEdgeId);

        // 6. Стоимость
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
            if (segment != null) {
                path.add(segment);
            }
            // Если участок отфильтрован — пропускаем, но идём дальше к корню
            current = edge.getTo();
        }
        return path;
    }
}
