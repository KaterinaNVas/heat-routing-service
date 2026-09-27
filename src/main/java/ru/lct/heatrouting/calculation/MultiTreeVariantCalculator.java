package ru.lct.heatrouting.calculation;

import org.springframework.stereotype.Component;
import ru.lct.heatrouting.network.MultiConnectionDraftBuilder;
import ru.lct.heatrouting.network.NewSegment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Calculates independent trees and charges the unconnected-OKS penalty once. */
@Component
public class MultiTreeVariantCalculator {
    private final MultiConnectionCalculator calculator;
    private final VariantCalculator costs;

    public MultiTreeVariantCalculator(MultiConnectionCalculator calculator, VariantCalculator costs) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.costs = Objects.requireNonNull(costs, "costs");
    }

    public VariantSummary calculateVariant(String variantId,
                                           List<MultiConnectionDraftBuilder.Draft> drafts,
                                           List<Object> unconnectedOksIds,
                                           Collection<Double> unconnectedFlows) {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(drafts, "drafts");
        if (drafts.isEmpty()) throw new IllegalArgumentException("Нет деревьев для расчёта");

        List<NewSegment> segments = new ArrayList<>();
        Map<String, Double> flows = new HashMap<>();
        Set<String> chambers = new HashSet<>();
        Set<String> oks = new HashSet<>();
        double constructionCost = 0;
        double chamberCost = 0;
        double tieInCost = 0;
        double totalLength = 0;
        int tieIns = 0;
        for (MultiConnectionDraftBuilder.Draft draft : drafts) {
            Objects.requireNonNull(draft, "draft");
            String chamberId = draft.getConnection().getChamberId();
            if (!chambers.add(chamberId)) {
                throw new IllegalArgumentException("Камера учтена в нескольких деревьях: " + chamberId);
            }
            draft.getDemands().keySet().forEach(node -> {
                if (!oks.add(node.getId())) {
                    throw new IllegalArgumentException("ОКС учтён дважды: " + node.getId());
                }
            });
            VariantSummary tree = calculator.calculate(variantId, draft.getRoot(),
                    draft.getParentEdge(), draft.getDemands(),
                    draft.getConnection().isExistingChamber(), List.of(), List.of());
            for (Map.Entry<String, Double> flow : tree.getFlowsByEdgeId().entrySet()) {
                if (flows.putIfAbsent(flow.getKey(), flow.getValue()) != null) {
                    throw new IllegalArgumentException("Повтор участка: " + flow.getKey());
                }
            }
            segments.addAll(tree.getSegments());
            constructionCost += tree.getConstructionCost();
            chamberCost += tree.getChamberConstructionCost();
            tieInCost += tree.getExistingChamberTieInCost();
            tieIns += tree.getExistingChamberTieInCount();
            totalLength += tree.getNewNetworkLength();
        }
        double penalty = unconnectedFlows == null || unconnectedFlows.isEmpty()
                ? 0 : costs.unconnectedPenalty(unconnectedFlows);
        double calculatedCost = constructionCost + penalty;
        return new VariantSummary(variantId, 1, segments, flows, constructionCost,
                chamberCost, tieIns, tieInCost, penalty, calculatedCost, totalLength,
                costs.score(calculatedCost, totalLength), unconnectedOksIds);
    }
}
