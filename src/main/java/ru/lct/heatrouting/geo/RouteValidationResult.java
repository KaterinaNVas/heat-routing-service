package ru.lct.heatrouting.geo;

import java.util.Collections;
import java.util.List;

public class RouteValidationResult {

    private final boolean valid;
    private final List<String> errors;
    private final List<Double> angles;

    private RouteValidationResult(boolean valid,
                                  List<String> errors,
                                  List<Double> angles) {
        this.valid = valid;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
        this.angles = angles == null ? List.of() : List.copyOf(angles);
    }

    public static RouteValidationResult ok(List<Double> angles) {
        return new RouteValidationResult(true, List.of(), angles);
    }

    public static RouteValidationResult fail(String error) {
        return new RouteValidationResult(false, List.of(error), List.of());
    }

    public static RouteValidationResult of(List<String> errors, List<Double> angles) {
        return new RouteValidationResult(false, errors, angles);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public List<Double> getAngles() {
        return Collections.unmodifiableList(angles);
    }

    public double getMaxAngle() {
        return angles.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    @Override
    public String toString() {
        return "RouteValidationResult{valid=" + valid
            + ", maxAngle=" + String.format("%.1f", getMaxAngle())
            + ", errors=" + errors + "}";
    }
}
