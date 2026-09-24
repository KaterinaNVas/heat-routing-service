package ru.lct.heatrouting.calculation;

import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.Edge;
import ru.lct.heatrouting.model.Node;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Расходы участков дерева новой сети, ориентированного к камере присоединения. */
@Component
public class FlowPropagator {

    /**
     * parentEdge: от каждого дочернего узла к его родителю; у root записи нет.
     * Edge.from является дочерним узлом, Edge.to — родительским.
     * Возвращает расходы по id новых участков без изменения объектов Edge.
     */
    public Map<String, Double> propagate(Node root, Map<Node, Edge> parentEdge,
                                          Map<Node, Double> demands) {
        if (root == null || parentEdge == null || demands == null || parentEdge.containsKey(root)) {
            throw new IllegalArgumentException("Некорректный корень или топология новой сети");
        }
        Map<String, Double> flows = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (Map.Entry<Node, Edge> entry : parentEdge.entrySet()) {
            Edge edge = entry.getValue();
            if (edge == null || !entry.getKey().equals(edge.getFrom()) ||
                    edge.getTo() == null || !ids.add(edge.getId())) {
                throw new IllegalArgumentException("Неверная ориентация или повтор id участка");
            }
            flows.put(edge.getId(), 0.0);
        }
        for (Map.Entry<Node, Double> demand : demands.entrySet()) {
            double value = demand.getValue() == null ? Double.NaN : demand.getValue();
            if (demand.getKey() == null || !Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Расход ОКС должен быть конечным и неотрицательным");
            }
            Node current = demand.getKey();
            Set<Node> visited = new HashSet<>();
            while (!current.equals(root)) {
                if (!visited.add(current)) {
                    throw new IllegalArgumentException("В новой сети найден цикл");
                }
                Edge edge = parentEdge.get(current);
                if (edge == null) {
                    throw new IllegalArgumentException("Для ОКС нет пути к камере присоединения");
                }
                flows.merge(edge.getId(), value, Double::sum);
                current = edge.getTo();
            }
        }
        return Collections.unmodifiableMap(flows);
    }
}
