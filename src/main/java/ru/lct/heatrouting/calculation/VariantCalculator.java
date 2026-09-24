package ru.lct.heatrouting.calculation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Стоимость и итоговый показатель варианта по разделу 6 приложения. */
@Component
public class VariantCalculator {
    private final double costWeight;
    private final double lengthWeight;

    public VariantCalculator(@Value("${routing.scoring.cost-weight:0.7}") double costWeight,
                             @Value("${routing.scoring.length-weight:0.3}") double lengthWeight) {
        if (!Double.isFinite(costWeight) || !Double.isFinite(lengthWeight) ||
                costWeight < 0 || lengthWeight < 0 ||
                Math.abs(costWeight + lengthWeight - 1.0) > 1e-9) {
            throw new IllegalArgumentException("Веса показателя должны быть неотрицательны и давать 1");
        }
        this.costWeight = costWeight;
        this.lengthWeight = lengthWeight;
    }

    public double chamberCost(int largestDiameter) {
        if (largestDiameter >= 50 && largestDiameter <= 200) return 3_000_000;
        if (largestDiameter >= 250 && largestDiameter <= 500) return 5_000_000;
        if (largestDiameter >= 600 && largestDiameter <= 1000) return 8_000_000;
        if (largestDiameter == 1200 || largestDiameter == 1400) return 12_000_000;
        throw new IllegalArgumentException("Неизвестный ДУ камеры: " + largestDiameter);
    }

    public double unconnectedPenalty(Collection<Double> flowsTph) {
        if (flowsTph == null) throw new IllegalArgumentException("Нет списка неподключённых ОКС");
        double total = 0;
        for (Double flow : flowsTph) {
            if (flow == null || !Double.isFinite(flow) || flow < 0) {
                throw new IllegalArgumentException("Некорректный расход ОКС");
            }
            total += 100_000_000 + 500_000 * flow;
        }
        return total;
    }

    public double constructionCost(double segmentCosts, double newChamberCosts,
                                   int existingChamberTieIns) {
        if (!Double.isFinite(segmentCosts) || segmentCosts < 0 ||
                !Double.isFinite(newChamberCosts) || newChamberCosts < 0 ||
                existingChamberTieIns < 0) {
            throw new IllegalArgumentException("Некорректные составляющие стоимости");
        }
        return segmentCosts + newChamberCosts + 5_000_000.0 * existingChamberTieIns;
    }

    public double score(double calculatedCost, double newNetworkLength) {
        if (!Double.isFinite(calculatedCost) || calculatedCost < 0 ||
                !Double.isFinite(newNetworkLength) || newNetworkLength < 0) {
            throw new IllegalArgumentException("Некорректная стоимость или длина");
        }
        return costWeight * calculatedCost / 25_000_000.0
                + lengthWeight * newNetworkLength / 100.0;
    }
}
