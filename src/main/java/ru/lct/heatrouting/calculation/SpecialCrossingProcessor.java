package ru.lct.heatrouting.calculation;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Component;
import ru.lct.heatrouting.model.Restriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработка специальных проходов новой сети через препятствия.
 * По ТЗ, таблица 5.1.
 */
@Component
public class SpecialCrossingProcessor {

    /**
     * Проверяет, пересекает ли маршрут запрещённые зоны.
     * Если да — бросает исключение.
     */
    public void validateNoForbiddenCrossings(LineString route,
                                             List<Restriction> restrictions) {
        if (route == null || restrictions == null) {
            throw new IllegalArgumentException("Аргументы не могут быть null");
        }
        for (Restriction restriction : restrictions) {
            if (!CrossingRules.hasRule(restriction.getRestrictionType())) {
                continue;
            }
            CrossingRules.Rule rule = CrossingRules.getRule(
                restriction.getRestrictionType());
            if (rule.allowed) {
                continue;  // спец. проход разрешён
            }
            Geometry geometry = restriction.getGeometry();
            if (geometry != null && route.intersects(geometry)) {
                throw new IllegalStateException(
                    "Маршрут пересекает запрещённую зону: "
                        + restriction.getRestrictionType()
                        + " (id=" + restriction.getId() + ")");
            }
        }
    }

    /**
     * Находит все препятствия, которые маршрут пересекает,
     * и вычисляет максимальный Кспец (при наложении).
     */
    public double findMaxKSpec(LineString route, List<Restriction> restrictions) {
        if (route == null || restrictions == null) {
            throw new IllegalArgumentException("Аргументы не могут быть null");
        }
        double maxKSpec = 1.0;
        for (Restriction restriction : restrictions) {
            if (!CrossingRules.hasRule(restriction.getRestrictionType())) {
                continue;
            }
            CrossingRules.Rule rule = CrossingRules.getRule(
                restriction.getRestrictionType());
            if (!rule.allowed) {
                continue;
            }
            Geometry geometry = restriction.getGeometry();
            if (geometry != null && route.intersects(geometry)) {
                maxKSpec = Math.max(maxKSpec, rule.kSpec);
            }
        }
        return maxKSpec;
    }

    /**
     * Проверяет, что для каждого спец. пересечения угол ≥ минимального.
     * Угол считается между осью препятствия и маршрутом.
     */
    public void validateAngles(LineString route, List<Restriction> restrictions) {
        // TODO: реализовать подсчёт углов
        // Пока — заглушка
    }
}

