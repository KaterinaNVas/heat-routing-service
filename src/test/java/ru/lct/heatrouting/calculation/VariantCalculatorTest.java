package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VariantCalculatorTest {

    private final VariantCalculator calculator = new VariantCalculator(0.7, 0.3);

    // ===== Существующий тест =====

    @Test
    void calculatesCostsAndConfirmedScore() {
        assertEquals(3_000_000, calculator.chamberCost(200));
        assertEquals(101_000_000, calculator.unconnectedPenalty(List.of(2.0)));
        assertEquals(13_974_800,
                calculator.constructionCost(8_974_800, 0, 1));
        assertEquals(0.7 * 13_974_800 / 25_000_000 + 0.3,
                calculator.score(13_974_800, 100));
    }

    // ===== chamberCost =====

    @Test
    void chamberCostForDifferentDiameters() {
        assertEquals(3_000_000, calculator.chamberCost(50));
        assertEquals(3_000_000, calculator.chamberCost(200));
        assertEquals(5_000_000, calculator.chamberCost(250));
        assertEquals(5_000_000, calculator.chamberCost(500));
        assertEquals(8_000_000, calculator.chamberCost(600));
        assertEquals(8_000_000, calculator.chamberCost(1000));
        assertEquals(12_000_000, calculator.chamberCost(1200));
        assertEquals(12_000_000, calculator.chamberCost(1400));
    }

    @Test
    void chamberCostThrowsForUnknownDiameter() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.chamberCost(30));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.chamberCost(1500));
    }

    // ===== unconnectedPenalty =====

    @Test
    void unconnectedPenaltyForSingleOks() {
        // 100 000 000 + 500 000 * 50 = 125 000 000
        assertEquals(125_000_000, calculator.unconnectedPenalty(List.of(50.0)));
    }

    @Test
    void unconnectedPenaltyForMultipleOks() {
        // 1) 100M + 500K*10 = 105M
        // 2) 100M + 500K*20 = 110M
        // 3) 100M + 500K*30 = 115M
        // Итого: 330M
        assertEquals(330_000_000,
                calculator.unconnectedPenalty(List.of(10.0, 20.0, 30.0)));
    }

    @Test
    void unconnectedPenaltyForEmptyList() {
        assertEquals(0, calculator.unconnectedPenalty(List.of()));
    }

    @Test
    void unconnectedPenaltyThrowsForNull() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.unconnectedPenalty(null));
    }

    @Test
    void unconnectedPenaltyThrowsForNegativeFlow() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.unconnectedPenalty(List.of(-5.0)));
    }

    @Test
    void unconnectedPenaltyThrowsForNaN() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.unconnectedPenalty(List.of(Double.NaN)));
    }

    // ===== constructionCost =====

    @Test
    void constructionCostWithMultipleTieIns() {
        // segmentCost=1M + chamberCost=3M + tieIns=2*5M = 14M
        assertEquals(14_000_000,
                calculator.constructionCost(1_000_000, 3_000_000, 2));
    }

    @Test
    void constructionCostWithZeroTieIns() {
        assertEquals(5_000_000,
                calculator.constructionCost(2_000_000, 3_000_000, 0));
    }

    @Test
    void constructionCostThrowsForNegativeSegmentCost() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.constructionCost(-1, 0, 0));
    }

    @Test
    void constructionCostThrowsForNegativeChamberCost() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.constructionCost(0, -1, 0));
    }

    @Test
    void constructionCostThrowsForNegativeTieIns() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.constructionCost(0, 0, -1));
    }

    // ===== score =====

    @Test
    void scoreForZeroCostAndZeroLength() {
        assertEquals(0.0, calculator.score(0, 0));
    }

    @Test
    void scoreForBaseCostAndBaseLength() {
        // 0.7 * (25M / 25M) + 0.3 * (100 / 100) = 1.0
        assertEquals(1.0, calculator.score(25_000_000, 100));
    }

    @Test
    void scoreThrowsForNegativeCost() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.score(-1, 100));
    }

    @Test
    void scoreThrowsForNegativeLength() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.score(100, -1));
    }

    // ===== combineKSpec (после добавления метода) =====

    @Test
    void combineKSpecTakesMaximum() {
        assertEquals(1.75, calculator.combineKSpec(1.25, 1.75, 1.6));
    }

    @Test
    void combineKSpecWithSingleValue() {
        assertEquals(1.6, calculator.combineKSpec(1.6));
    }

    @Test
    void combineKSpecWithEmptyValuesReturnsDefault() {
        assertEquals(1.0, calculator.combineKSpec());
    }

    @Test
    void combineKSpecThrowsForNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.combineKSpec(-1.0));
    }

    // ===== Веса =====

    @Test
    void acceptsAlternativeWeights() {
        VariantCalculator alt = new VariantCalculator(0.5, 0.5);
        assertEquals(0.5 * 10_000_000 / 25_000_000 + 0.5 * 50 / 100,
                alt.score(10_000_000, 50), 1e-9);
    }

    @Test
    void rejectsWeightsNotSummingToOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new VariantCalculator(0.5, 0.4));
    }
}
