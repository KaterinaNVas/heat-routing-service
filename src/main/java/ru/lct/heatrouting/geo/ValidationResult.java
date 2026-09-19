package ru.lct.heatrouting.geo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {

    private final boolean valid;
    private final List<String> errors;

    private ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(errors);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, new ArrayList<>());
    }

    public static ValidationResult fail(String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return new ValidationResult(false, errors);
    }

    public static ValidationResult of(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return ok();
        }
        return new ValidationResult(false, errors);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{OK}";
        }
        return "ValidationResult{INVALID, errors=" + errors + "}";
    }
}