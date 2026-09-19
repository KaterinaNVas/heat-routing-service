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
}
