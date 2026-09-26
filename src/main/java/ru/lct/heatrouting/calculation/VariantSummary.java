package ru.lct.heatrouting.calculation;

import ru.lct.heatrouting.network.NewSegment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class VariantSummary {

    private final String variantId;
    private final int rank;
    private final List<NewSegment> segments;
    private final Map<String, Double> flowsByEdgeId;
    private final double constructionCost;
    private final double chamberConstructionCost;
    private final int existingChamberTieInCount;
    private final double existingChamberTieInCost;
    private final double unconnectedPenalty;
    private final double calculatedCost;
    private final double newNetworkLength;
    private final double score;
    private final List<Object> unconnectedOksIds;

    public VariantSummary(String variantId,
                          int rank,
                          List<NewSegment> segments,
                          Map<String, Double> flowsByEdgeId,
                          double constructionCost,
                          double chamberConstructionCost,
                          int existingChamberTieInCount,
                          double existingChamberTieInCost,
                          double unconnectedPenalty,
                          double calculatedCost,
                          double newNetworkLength,
                          double score,
                          List<Object> unconnectedOksIds) {
        this.variantId = variantId;
        this.rank = rank;
        this.segments = segments == null ? List.of() : List.copyOf(segments);
        this.flowsByEdgeId = flowsByEdgeId == null ? Map.of() : Map.copyOf(flowsByEdgeId);
        this.constructionCost = constructionCost;
        this.chamberConstructionCost = chamberConstructionCost;
        this.existingChamberTieInCount = existingChamberTieInCount;
        this.existingChamberTieInCost = existingChamberTieInCost;
        this.unconnectedPenalty = unconnectedPenalty;
        this.calculatedCost = calculatedCost;
        this.newNetworkLength = newNetworkLength;
        this.score = score;
        this.unconnectedOksIds = unconnectedOksIds == null
                ? List.of() : List.copyOf(unconnectedOksIds);
    }

    public String getVariantId() { return variantId; }
    public int getRank() { return rank; }
    public List<NewSegment> getSegments() { return Collections.unmodifiableList(segments); }
    public Map<String, Double> getFlowsByEdgeId() { return flowsByEdgeId; }
    public double getConstructionCost() { return constructionCost; }
    public double getChamberConstructionCost() { return chamberConstructionCost; }
    public int getExistingChamberTieInCount() { return existingChamberTieInCount; }
    public double getExistingChamberTieInCost() { return existingChamberTieInCost; }
    public double getUnconnectedPenalty() { return unconnectedPenalty; }
    public double getCalculatedCost() { return calculatedCost; }
    public double getNewNetworkLength() { return newNetworkLength; }
    public double getScore() { return score; }
    public List<Object> getUnconnectedOksIds() { return unconnectedOksIds; }

    @Override
    public String toString() {
        return "VariantSummary{" + variantId + ", rank=" + rank
                + ", cost=" + calculatedCost + ", length=" + newNetworkLength
                + ", score=" + score + "}";
    }
}
