package ru.lct.heatrouting.calculation;

import ru.lct.heatrouting.model.RestrictionType;

import java.util.Map;

/**
 * Справочник правил пересечения препятствий.
 * Данные из ТЗ, таблица 5.1.
 */
public class CrossingRules {

    /**
     * Правило для одного типа препятствия.
     */
    public static final class Rule {
        public final boolean allowed;              // разрешён ли спец. проход
        public final double minHorizontalDistance; // минимальное горизонтальное расстояние, м
        public final double minAngleDegrees;       // минимальный угол пересечения (0 = не задан)
        public final double verticalClearance;     // минимальный вертикальный просвет, м
        public final double kSpec;                 // коэффициент специального прохода

        public Rule(boolean allowed, double minHorizontalDistance,
                    double minAngleDegrees, double verticalClearance, double kSpec) {
            this.allowed = allowed;
            this.minHorizontalDistance = minHorizontalDistance;
            this.minAngleDegrees = minAngleDegrees;
            this.verticalClearance = verticalClearance;
            this.kSpec = kSpec;
        }
    }

    private static final Map<RestrictionType, Rule> RULES = Map.ofEntries(
        Map.entry(RestrictionType.ROAD,            new Rule(true,  1.5, 45.0, 1.0, 1.60)),
        Map.entry(RestrictionType.TRAM_TRACKS,     new Rule(true,  1.5, 45.0, 1.2, 1.75)),
        Map.entry(RestrictionType.GAS_PIPELINE,    new Rule(true,  2.0,  0.0, 0.2, 1.25)),
        Map.entry(RestrictionType.POWER_CABLE,     new Rule(true,  2.0,  0.0, 0.5, 1.15)),
        Map.entry(RestrictionType.HEAT_NETWORK,    new Rule(true,  1.0,  0.0, 0.5, 1.05)),
        Map.entry(RestrictionType.OKS,             new Rule(false, 5.0,  0.0, 0.0, 1.0)),
        Map.entry(RestrictionType.PARK,            new Rule(false, 1.0,  0.0, 0.0, 1.0)),
        Map.entry(RestrictionType.SOCIAL_AREA,     new Rule(false, 1.0,  0.0, 0.0, 1.0)),
        Map.entry(RestrictionType.PROHIBITED_SITE, new Rule(false, 1.0,  0.0, 0.0, 1.0)),
        Map.entry(RestrictionType.WATER,           new Rule(false, 1.0,  0.0, 0.0, 1.0)),
        Map.entry(RestrictionType.RAILWAY,         new Rule(false, 1.0,  0.0, 0.0, 1.0))
    );

    public static Rule getRule(RestrictionType type) {
        Rule rule = RULES.get(type);
        if (rule == null) {
            throw new IllegalArgumentException(
                "Нет правила для типа: " + type);
        }
        return rule;
    }

    public static boolean hasRule(RestrictionType type) {
        return RULES.containsKey(type);
    }
}
