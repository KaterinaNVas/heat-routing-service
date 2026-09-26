package ru.lct.heatrouting.cost;

import ru.lct.heatrouting.network.ExistingSegment;
import ru.lct.heatrouting.network.NewSegment;

import java.util.List;

public class SegmentCostCalculator {

    public static final double NORMAL_DEPTH_M = 3.0;
    public static final double DEPTH_COST_SLOPE = 0.10;

    private final DiameterCatalog catalog;

    public SegmentCostCalculator(DiameterCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("DiameterCatalog не может быть null");
        }
        this.catalog = catalog;
    }

    public double calculateNewSegmentCost(NewSegment segment) {
        return calculateNewSegmentCost(segment, 1.0, 1.0);
    }

    public double calculateNewSegmentCost(NewSegment segment, double specialCoefficient) {
        return calculateNewSegmentCost(segment, 1.0, specialCoefficient);
    }

    public double calculateNewSegmentCost(NewSegment segment,
                                          double depthCoefficient,
                                          double specialCoefficient) {
        if (segment == null) {
            throw new IllegalArgumentException("Участок не может быть null");
        }
        if (!Double.isFinite(depthCoefficient) || depthCoefficient < 1.0) {
            throw new IllegalArgumentException("Kгл должен быть конечным и не меньше 1");
        }
        if (!Double.isFinite(specialCoefficient) || specialCoefficient < 1.0) {
            throw new IllegalArgumentException("Kспец должен быть конечным и не меньше 1");
        }
        return segment.getLength()
                * catalog.getNewBuildCost(segment.getDiameter())
                * depthCoefficient
                * specialCoefficient;
    }

    public double depthCoefficient(double depthMeters) {
        if (!Double.isFinite(depthMeters) || depthMeters < 0) {
            throw new IllegalArgumentException("Глубина должна быть конечной и неотрицательной");
        }
        if (depthMeters <= NORMAL_DEPTH_M) {
            return 1.0;
        }
        return 1.0 + DEPTH_COST_SLOPE * (depthMeters - NORMAL_DEPTH_M);
    }

    public double averageDepthCoefficient(double depthStartMeters, double depthEndMeters) {
        return 0.5 * (depthCoefficient(depthStartMeters) + depthCoefficient(depthEndMeters));
    }

    public double calculateReconstructionCost(ExistingSegment segment, int requiredDn) {
        if (segment == null) {
            throw new IllegalArgumentException("Участок не может быть null");
        }
        if (requiredDn <= 0) {
            throw new IllegalArgumentException("Требуемый ДУ должен быть положительным");
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
            if (segment == null) {
                throw new IllegalArgumentException("Участок в цепочке не может быть null");
            }
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
