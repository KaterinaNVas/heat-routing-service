package ru.lct.heatrouting.calculation;

import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

import java.util.HashMap;
import java.util.Map;

@Component
public class TopologyValidator {

    /**
     * Проверяет, что ДУ не уменьшается по пути от каждого ОКС к корню.
     * По ТЗ: по направлению к месту присоединения расход растёт,
     * значит ДУ не уменьшается.
     */
    public void validateDiameterMonotonicity(Node root,
                                             Map<Node, Edge> parentEdge,
                                             Map<Node, Double> demands) {
        if (root == null || parentEdge == null || demands == null) {
            throw new IllegalArgumentException("Аргументы не могут быть null");
        }
        for (Node node : demands.keySet()) {
            if (node.equals(root)) continue;
            Node current = node;
            int prevDn = Integer.MIN_VALUE;
            while (!current.equals(root)) {
                Edge edge = parentEdge.get(current);
                if (edge == null) {
                    throw new IllegalArgumentException(
                        "ОКС " + node.getId() + " не имеет пути к корню");
                }
                if (edge.getDiameter() < prevDn) {
                    throw new IllegalStateException(
                        "ДУ уменьшается к месту присоединения: участок "
                            + edge.getId() + " имеет ДУ " + edge.getDiameter()
                            + ", а предыдущий был " + prevDn);
                }
                prevDn = edge.getDiameter();
                current = edge.getTo();
            }
        }
    }

    /**
     * Проверяет, что все ветвления (узлы с 2+ входящими рёбрами) —
     * тепловые камеры. По ТЗ: разветвления новой сети выполняются
     * только в тепловых камерах.
     */
    public void validateBranchingsInChambers(Map<Node, Edge> parentEdge) {
        if (parentEdge == null) {
            throw new IllegalArgumentException("parentEdge не может быть null");
        }
        Map<Node, Integer> childCount = new HashMap<>();
        for (Edge edge : parentEdge.values()) {
            childCount.merge(edge.getTo(), 1, Integer::sum);
        }
        for (Map.Entry<Node, Integer> entry : childCount.entrySet()) {
            if (entry.getValue() >= 2
                && !"heat_chamber".equals(entry.getKey().getObjectType())) {
                throw new IllegalStateException(
                    "Разветвление не в камере: узел " + entry.getKey().getId()
                        + " (тип " + entry.getKey().getObjectType()
                        + ") имеет " + entry.getValue() + " входящих участков");
            }
        }
    }

    /**
    * Проверяет, что ПОДОБРАННЫЙ ДУ не уменьшается по пути от ОКС к корню.
    * По ТЗ: по направлению к месту присоединения расход растёт,
    * значит подобранный ДУ не уменьшается.
    *
    * @param root             корневой узел (камера)
    * @param parentEdge       карта: узел → ребро к родителю
    * @param demands          карта: ОКС → расход
    * @param pickedDiameters  карта: edgeId → подобранный ДУ
    */
   public void validatePickedDiameterMonotonicity(Node root,
                                                  Map<Node, Edge> parentEdge,
                                                  Map<Node, Double> demands,
                                                  Map<String, Integer> pickedDiameters) {
        if (root == null || parentEdge == null || demands == null || pickedDiameters == null) {
            throw new IllegalArgumentException("Аргументы не могут быть null");
        }
        for (Node oks : demands.keySet()) {
            if (oks.equals(root)) continue;
               Node current = oks;
            int previousDn = -1;
            while (!current.equals(root)) {
                    Edge edge = parentEdge.get(current);
                if (edge == null) {
                         throw new IllegalArgumentException(
                                "Нет пути к корню от узла " + current.getId());
                    }
                    Integer dn = pickedDiameters.get(edge.getId());
                    if (dn == null) {
                    // Участок отфильтрован (нулевой расход) — пропускаем
                        current = edge.getTo();
                        continue;
                    }
                    if (previousDn >= 0 && dn < previousDn) {
                            throw new IllegalStateException(
                            "Подобранный ДУ уменьшается к месту присоединения: "
                                + previousDn + " → " + dn
                                + " (ребро " + edge.getId() + ")");
                    }
                    previousDn = dn;
                    current = edge.getTo();
                }
    }
   }
}
