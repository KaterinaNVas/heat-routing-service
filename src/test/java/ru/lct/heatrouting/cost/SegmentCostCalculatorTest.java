package ru.lct.heatrouting.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.lct.heatrouting.network.ExistingSegment;
import ru.lct.heatrouting.network.NewSegment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SegmentCostCalculatorTest {

    private DiameterCatalog catalog;
    private SegmentCostCalculator calculator;

    @BeforeEach
    void setUp() {
        catalog = new DiameterCatalog();
        calculator = new SegmentCostCalculator(catalog);
    }

    // --- Стоимость нового участка ---

    @Test
    void calculateNewSegmentCost_returnsLengthTimesCostPerMeter() {
        NewSegment segment = new NewSegment("N1", 100, 100.0);
        assertEquals(8_974_800.0, calculator.calculateNewSegmentCost(segment), 0.001);
    }

    @Test
    void calculateNewSegmentCost_handlesZeroLength() {
        NewSegment segment = new NewSegment("N1", 100, 0.0);
        assertEquals(0.0, calculator.calculateNewSegmentCost(segment), 0.001);
    }

    @Test
    void calculateNewSegmentCost_throwsForUnknownDn() {
        NewSegment segment = new NewSegment("N1", 999, 100.0);
        assertThrows(IllegalArgumentException.class,
            () -> calculator.calculateNewSegmentCost(segment));
    }

    // --- Реконструкция ---

    @Test
    void calculateReconstructionCost_returnsZeroIfNotNeeded() {
        ExistingSegment segment = new ExistingSegment("E1", 200, 100.0);
        assertEquals(0.0, calculator.calculateReconstructionCost(segment, 150), 0.001);
        assertEquals(0.0, calculator.calculateReconstructionCost(segment, 200), 0.001);
    }

    @Test
    void calculateReconstructionCost_returnsCorrectValue() {
        ExistingSegment segment = new ExistingSegment("E1", 100, 100.0);
        assertEquals(15_229_500.0,
            calculator.calculateReconstructionCost(segment, 150), 0.001);
    }

    // --- Предельная длина ---

    @Test
    void checkMaxLength_returnsTrueForEmptyChain() {
        assertTrue(calculator.checkMaxLength(List.of()));
        assertTrue(calculator.checkMaxLength(null));
    }

    @Test
    void checkMaxLength_returnsTrueIfUnderLimit() {
        List<NewSegment> chain = List.of(
            new NewSegment("N1", 100, 300.0),
            new NewSegment("N2", 100, 100.0)
        );
        assertTrue(calculator.checkMaxLength(chain));
    }

    @Test
    void checkMaxLength_returnsFalseIfOverLimit() {
        List<NewSegment> chain = List.of(
            new NewSegment("N1", 100, 300.0),
            new NewSegment("N2", 100, 200.0)
        );
        assertFalse(calculator.checkMaxLength(chain));
    }

    @Test
    void checkMaxLength_resetsCounterOnDnChange() {
        List<NewSegment> chain = List.of(
            new NewSegment("N1", 100, 300.0),
            new NewSegment("N2", 150, 600.0),
            new NewSegment("N3", 100, 300.0)
        );
        assertTrue(calculator.checkMaxLength(chain));
    }

    @Test
    void checkMaxLength_returnsFalseIfMiddleChainOverLimit() {
        List<NewSegment> chain = List.of(
            new NewSegment("N1", 100, 300.0),
            new NewSegment("N2", 150, 800.0),
            new NewSegment("N3", 100, 300.0)
        );
        assertFalse(calculator.checkMaxLength(chain));
    }

    // --- Kгл: коэффициент глубины (ТЗ 5, раздел 6) ---

    @Test
    void depthCoefficient_atMinimumDepth_returnsOne() {
        assertEquals(1.0, calculator.depthCoefficient(0.7), 1e-9);
    }

    @Test
    void depthCoefficient_atNormalDepth_returnsOne() {
        assertEquals(1.0, calculator.depthCoefficient(3.0), 1e-9);
    }

    @Test
    void depthCoefficient_justAboveNormalDepth_returnsOnePointZeroFive() {
        assertEquals(1.05, calculator.depthCoefficient(3.5), 1e-9);
    }

    @Test
    void depthCoefficient_atFourMeters_returnsOnePointOne() {
        assertEquals(1.10, calculator.depthCoefficient(4.0), 1e-9);
    }

    @Test
    void depthCoefficient_atFiveMeters_returnsOnePointTwo() {
        assertEquals(1.20, calculator.depthCoefficient(5.0), 1e-9);
    }

    @Test
    void depthCoefficient_atTenMeters_returnsOnePointSeven() {
        assertEquals(1.70, calculator.depthCoefficient(10.0), 1e-9);
    }

    @Test
    void depthCoefficient_throwsForNegativeDepth() {
        assertThrows(IllegalArgumentException.class,
            () -> calculator.depthCoefficient(-1.0));
    }

    @Test
    void averageDepthCoefficient_betweenThreeAndFive_returnsOnePointOne() {
        assertEquals(1.10, calculator.averageDepthCoefficient(3.0, 5.0), 1e-9);
    }

    @Test
    void averageDepthCoefficient_betweenThreeAndThree_returnsOne() {
        assertEquals(1.0, calculator.averageDepthCoefficient(3.0, 3.0), 1e-9);
    }

    @Test
    void calculateNewSegmentCost_appliesDepthCoefficient() {
        NewSegment segment = new NewSegment("N1", 100, 100.0);
        double expected = 100.0 * 89_748.0 * 1.10;
        assertEquals(expected, calculator.calculateNewSegmentCost(segment, 1.10, 1.0), 0.001);
    }

    @Test
    void calculateNewSegmentCost_appliesBothDepthAndSpecialCoefficients() {
        NewSegment segment = new NewSegment("N1", 100, 100.0);
        double expected = 100.0 * 89_748.0 * 1.10 * 1.60;
        assertEquals(expected, calculator.calculateNewSegmentCost(segment, 1.10, 1.60), 0.001);
    }

    @Test
    void calculateNewSegmentCost_throwsForDepthCoefficientBelowOne() {
        NewSegment segment = new NewSegment("N1", 100, 100.0);
        assertThrows(IllegalArgumentException.class,
            () -> calculator.calculateNewSegmentCost(segment, 0.5, 1.0));
    }

    @Test
    void calculateNewSegmentCost_throwsForSpecialCoefficientBelowOne() {
        NewSegment segment = new NewSegment("N1", 100, 100.0);
        assertThrows(IllegalArgumentException.class,
            () -> calculator.calculateNewSegmentCost(segment, 1.0, 0.5));
    }
}
