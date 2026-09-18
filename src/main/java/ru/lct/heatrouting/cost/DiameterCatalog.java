package ru.lct.heatrouting.cost;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

public class DiameterCatalog {

    private final Map<Integer, Double> capacity;
    private final Map<Integer, Double> maxLength;
    private final Map<Integer, Double> newBuildCost;
    private final Map<Integer, Double> reconstructionCost;

    public DiameterCatalog() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getClassLoader()
                .getResourceAsStream("catalogs.json");
            if (is == null) {
                throw new IllegalStateException(
                    "Файл catalogs.json не найден в resources");
            }
            CatalogsData data = mapper.readValue(is, CatalogsData.class);
            this.capacity = new TreeMap<>(data.capacities);
            this.maxLength = new TreeMap<>(data.maxLengths);
            this.newBuildCost = new TreeMap<>(data.newBuildCosts);
            this.reconstructionCost = new TreeMap<>(data.reconstructionCosts);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки справочников", e);
        }
    }

    public int findMinimalDiameter(double flow) {
        if (flow < 0) {
            throw new IllegalArgumentException(
                "Расход не может быть отрицательным: " + flow);
        }
        for (Map.Entry<Integer, Double> entry : capacity.entrySet()) {
            if (entry.getValue() >= flow) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException(
            "Нет подходящего DN для расхода " + flow + " т/ч. " +
            "Максимальный DN " + getMaxDiameter() +
            " пропускает " + getCapacity(getMaxDiameter()) + " т/ч."
        );
    }

    public double getCapacity(int dn) {
        Double value = capacity.get(dn);
        if (value == null) throw new IllegalArgumentException("Неизвестный DN: " + dn);
        return value;
    }

    public double getMaxLength(int dn) {
        Double value = maxLength.get(dn);
        if (value == null) throw new IllegalArgumentException("Неизвестный DN: " + dn);
        return value;
    }

    public double getNewBuildCost(int dn) {
        Double value = newBuildCost.get(dn);
        if (value == null) throw new IllegalArgumentException("Неизвестный DN: " + dn);
        return value;
    }

    public double getReconstructionCost(int requiredDn) {
        Double cost = reconstructionCost.get(requiredDn);
        if (cost == null) throw new IllegalArgumentException("Неизвестный DN: " + requiredDn);
        return cost;
    }

    public int getMaxDiameter() {
        return ((TreeMap<Integer, Double>) capacity).lastKey();
    }
}
