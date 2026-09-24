package ru.lct.heatrouting.calculation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VariantCalculatorTest {
    @Test
    void calculatesCostsAndConfirmedScore() {
        VariantCalculator calculator = new VariantCalculator(0.7, 0.3);
        assertEquals(3_000_000, calculator.chamberCost(200));
        assertEquals(101_000_000, calculator.unconnectedPenalty(List.of(2.0)));
        assertEquals(13_974_800,
                calculator.constructionCost(8_974_800, 0, 1));
        assertEquals(0.7 * 13_974_800 / 25_000_000 + 0.3,
                calculator.score(13_974_800, 100));
    }
}
