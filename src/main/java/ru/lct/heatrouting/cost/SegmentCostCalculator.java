package ru.lct.heatrouting.cost;

import ru.lct.heatrouting.network.ExistingSegment;
import ru.lct.heatrouting.network.NewSegment;

import java.util.List;

public class SegmentCostCalculator {

    private final DiameterCatalog catalog;

    public SegmentCostCalculator(DiameterCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("DiameterCatalog не может быть null");
        }
        this.catalog = catalog;
    }

    public double calculateNewSegmentCost(NewSegment segment) {
        return calculateNewSegmentCost(segment, 1.0);
    }

    /** В 2D Kгл=1; для наложенных специальных проходов передаётся максимум Kспец. */
    public double calculateNewSegmentCost(NewSegment segment, double specialCoefficient) {
        if (segment == null) {
            throw new IllegalArgumentException("Участок не может быть null");
        }
        if (!Double.isFinite(specialCoefficient) || specialCoefficient < 1.0) {
            throw new IllegalArgumentException("Kспец должен быть конечным и не меньше 1");
        }
        return segment.getLength() * catalog.getNewBuildCost(segment.getDiameter())
                * specialCoefficient;
    }

    public double calculateReconstructionCost(ExistingSegment segment, int requiredDn) {
        if (segment == null) {
            throw new IllegalArgumentException("Участок не может быть null");
        }
        if (segment.getDiameter() >= requiredDn) {
            return 0.0;
        }
        return segment.getLength() * catalog.getReconstructionCost(requiredDn);
    }

    public boolean checkMaxLength(List<NewSegment> chain) {
        if (chain == null || chain.isEmpty()) {
            return true;
        }
        int currentDn = chain.get(0).getDiameter();
        double accumulatedLength = 0.0;
        for (NewSegment segment : chain) {
            if (segment.getDiameter() != currentDn) {
                if (accumulatedLength > catalog.getMaxLength(currentDn)) {
                    return false;
                }
                currentDn = segment.getDiameter();
                accumulatedLength = 0.0;
            }
            accumulatedLength += segment.getLength();
        }
        return accumulatedLength <= catalog.getMaxLength(currentDn);
    }
}
