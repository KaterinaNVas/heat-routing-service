package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VariantRankerTest {

    private final VariantRanker ranker = new VariantRanker();

    private VariantSummary summary(String id, double score) {
        return new VariantSummary(
            id, 1, List.of(), Map.of(),
            0, 0, 0, 0, 0, 0, 0, score, List.of());
    }

    @Test
    void ranksByScoreAscending() {
        VariantSummary a = summary("a", 0.5);
        VariantSummary b = summary("b", 0.2);
        VariantSummary c = summary("c", 0.8);

        List<VariantSummary> ranked = ranker.rank(List.of(a, b, c), 3);

        assertEquals(3, ranked.size());
        assertEquals("b", ranked.get(0).getVariantId());
        assertEquals("a", ranked.get(1).getVariantId());
        assertEquals("c", ranked.get(2).getVariantId());
    }

    @Test
    void assignsRankStartingFromOne() {
        VariantSummary a = summary("a", 0.5);
        VariantSummary b = summary("b", 0.2);

        List<VariantSummary> ranked = ranker.rank(List.of(a, b), 2);

        assertEquals(1, ranked.get(0).getRank());
        assertEquals(2, ranked.get(1).getRank());
    }

    @Test
    void limitsToTopN() {
        VariantSummary a = summary("a", 0.5);
        VariantSummary b = summary("b", 0.2);
        VariantSummary c = summary("c", 0.8);

        List<VariantSummary> ranked = ranker.rank(List.of(a, b, c), 2);

        assertEquals(2, ranked.size());
        assertEquals("b", ranked.get(0).getVariantId());
        assertEquals("a", ranked.get(1).getVariantId());
    }

    @Test
    void acceptsEmptyList() {
        assertEquals(0, ranker.rank(List.of(), 3).size());
    }

    @Test
    void rejectsNullList() {
        assertThrows(NullPointerException.class,
            () -> ranker.rank(null, 3));
    }

    @Test
    void rejectsNonPositiveTopN() {
        assertThrows(IllegalArgumentException.class,
            () -> ranker.rank(List.of(), 0));
    }
}
