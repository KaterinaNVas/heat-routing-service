package ru.lct.heatrouting.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiameterCatalogTest {

    private DiameterCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DiameterCatalog();
    }

    @Test
    void findMinimalDiameter_returnsSmallestFittingDn() {
        assertEquals(50,   catalog.findMinimalDiameter(3.5));
        assertEquals(65,   catalog.findMinimalDiameter(3.6));
        assertEquals(65,   catalog.findMinimalDiameter(8.3));
        assertEquals(100,  catalog.findMinimalDiameter(22.3));
        assertEquals(150,  catalog.findMinimalDiameter(65.1));
        assertEquals(200,  catalog.findMinimalDiameter(152.3));
        assertEquals(400,  catalog.findMinimalDiameter(500.0));
        assertEquals(1200, catalog.findMinimalDiameter(9400.0));
        assertEquals(1400, catalog.findMinimalDiameter(15013.0));
        assertEquals(1400, catalog.findMinimalDiameter(22501.9));
    }

    @Test
    void findMinimalDiameter_throwsIfFlowTooLarge() {
        Exception ex = assertThrows(IllegalArgumentException.class,
            () -> catalog.findMinimalDiameter(30000.0));
        assertTrue(ex.getMessage().contains("Нет подходящего DN"));
    }

    @Test
    void findMinimalDiameter_throwsIfFlowNegative() {
        assertThrows(IllegalArgumentException.class,
            () -> catalog.findMinimalDiameter(-1.0));
    }

    @Test
    void getCapacity_returnsCorrectValues() {
        assertEquals(3.5,     catalog.getCapacity(50), 0.001);
        assertEquals(22.3,    catalog.getCapacity(100), 0.001);
        assertEquals(9391.8,  catalog.getCapacity(1000), 0.001);
        assertEquals(22501.9, catalog.getCapacity(1400), 0.001);
    }

    @Test
    void getReconstructionCost_returnsCorrectValues() {
        assertEquals(133694.0, catalog.getReconstructionCost(100), 0.001);
        assertEquals(181766.0, catalog.getReconstructionCost(200), 0.001);
        assertEquals(553607.0, catalog.getReconstructionCost(900), 0.001);
    }

    @Test
    void getReconstructionCost_throwsForUnknownDn() {
        assertThrows(IllegalArgumentException.class,
            () -> catalog.getReconstructionCost(999));
    }

    @Test
    void getMaxDiameter_returnsMax() {
        assertEquals(1400, catalog.getMaxDiameter());
    }

    @Test
    void findMinimalDiameter_considersBothFlowAndContinuousLength() {
        assertEquals(100, catalog.findMinimalDiameter(20, 400));
        assertEquals(125, catalog.findMinimalDiameter(20, 420));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.findMinimalDiameter(20, 12_000));
    }
}
