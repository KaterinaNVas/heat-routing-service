package ru.lct.heatrouting.calculation;

import org.springframework.stereotype.Component;
import ru.lct.heatrouting.cost.DiameterCatalog;
import ru.lct.heatrouting.cost.SegmentCostCalculator;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.network.NewSegment;
import ru.lct.heatrouting.network.SingleConnectionDraftBuilder;

import java.util.Map;
import java.util.Objects;

/** Расчёт одного нового участка в базовом 2D-режиме. */
@Component
public class SingleConnectionCalculator {
    private final FlowPropagator flowPropagator;
    private final VariantCalculator variantCalculator;
    private final DiameterCatalog catalog = new DiameterCatalog();
    private final SegmentCostCalculator segmentCosts = new SegmentCostCalculator(catalog);

    public SingleConnectionCalculator(FlowPropagator flowPropagator,
                                      VariantCalculator variantCalculator) {
        this.flowPropagator = Objects.requireNonNull(flowPropagator);
        this.variantCalculator = Objects.requireNonNull(variantCalculator);
    }

    public CalculatedConnection calculate(SingleConnectionDraftBuilder.Draft draft) {
        Objects.requireNonNull(draft, "draft");
        if (draft.getParentEdge().size() != 1 || draft.getDemands().size() != 1) {
            throw new IllegalArgumentException("Этот расчёт предназначен для одного ОКС и одного участка");
        }
        Edge provisional = draft.getParentEdge().values().iterator().next();
        Map<String, Double> flows = flowPropagator.propagate(draft.getRoot(),
                draft.getParentEdge(), draft.getDemands());
        double flow = flows.get(provisional.getId());
        double length = provisional.getLengthMeters();
        int requiredDiameter = catalog.findMinimalDiameter(flow, length);
        if (requiredDiameter != provisional.getDiameter()) {
            throw new DiameterChangeRequiredException(requiredDiameter);
        }
        Edge calculated = new Edge(provisional.getId(), provisional.getFrom(),
                provisional.getTo(), provisional.getGeometry(), requiredDiameter, flow, "base");
        double segmentCost = segmentCosts.calculateNewSegmentCost(
                new NewSegment(calculated.getId(), requiredDiameter, length));
        double chamberCost = draft.getConnection().isExistingChamber() ? 0
                : variantCalculator.chamberCost(requiredDiameter);
        int tieIns = draft.getConnection().isExistingChamber() ? 1 : 0;
        double constructionCost = variantCalculator.constructionCost(segmentCost, chamberCost, tieIns);
        return new CalculatedConnection(calculated, segmentCost, chamberCost, tieIns,
                constructionCost, variantCalculator.score(constructionCost, length));
    }

    /** Требуется повторно построить путь с габаритом найденного ДУ. */
    public static final class DiameterChangeRequiredException extends IllegalStateException {
        private final int requiredDiameter;
        private DiameterChangeRequiredException(int requiredDiameter) {
            super("Перестройте маршрут с ДУ " + requiredDiameter + " и проверьте ограничения заново");
            this.requiredDiameter = requiredDiameter;
        }
        public int getRequiredDiameter() { return requiredDiameter; }
    }

    public static final class CalculatedConnection {
        private final Edge edge;
        private final double segmentCost;
        private final double chamberCost;
        private final int existingChamberTieInCount;
        private final double constructionCost;
        private final double score;

        private CalculatedConnection(Edge edge, double segmentCost, double chamberCost,
                                     int existingChamberTieInCount, double constructionCost,
                                     double score) {
            this.edge = edge;
            this.segmentCost = segmentCost;
            this.chamberCost = chamberCost;
            this.existingChamberTieInCount = existingChamberTieInCount;
            this.constructionCost = constructionCost;
            this.score = score;
        }
        public Edge getEdge() { return edge; }
        public double getSegmentCost() { return segmentCost; }
        public double getChamberCost() { return chamberCost; }
        public int getExistingChamberTieInCount() { return existingChamberTieInCount; }
        public double getConstructionCost() { return constructionCost; }
        public double getScore() { return score; }
    }
}
