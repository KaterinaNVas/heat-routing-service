package ru.lct.heatrouting.calculation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ранжирует варианты по показателю score (0.7 × стоимость + 0.3 × длина).
 * Возвращает до topN лучших вариантов (меньший score — лучше).
 */
@Component
public class VariantRanker {

    /**
     * Сортирует варианты по возрастанию score и возвращает не более topN.
     * Присваивает поле rank: 1 — лучший, 2 — следующий и т.д.
     */
    public List<VariantSummary> rank(List<VariantSummary> variants, int topN) {
        Objects.requireNonNull(variants, "variants");
        if (topN <= 0) {
            throw new IllegalArgumentException("topN должен быть положительным");
        }

        List<VariantSummary> sorted = new ArrayList<>(variants);
        sorted.sort(Comparator.comparingDouble(VariantSummary::getScore));

        int limit = Math.min(topN, sorted.size());
        List<VariantSummary> result = new ArrayList<>(limit);

        for (int i = 0; i < limit; i++) {
            VariantSummary original = sorted.get(i);
            result.add(withRank(original, i + 1));
        }
        return result;
    }

    /**
     * Создаёт копию VariantSummary с указанным rank.
     */
    private VariantSummary withRank(VariantSummary original, int rank) {
        return new VariantSummary(
            original.getVariantId(),
            rank,
            original.getSegments(),
            original.getFlowsByEdgeId(),
            original.getConstructionCost(),
            original.getChamberConstructionCost(),
            original.getExistingChamberTieInCount(),
            original.getExistingChamberTieInCost(),
            original.getUnconnectedPenalty(),
            original.getCalculatedCost(),
            original.getNewNetworkLength(),
            original.getScore(),
            original.getUnconnectedOksIds()
        );
    }
}
