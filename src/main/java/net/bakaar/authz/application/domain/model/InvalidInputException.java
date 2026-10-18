package net.bakaar.authz.application.domain.model;

import java.util.Map;

public class InvalidInputException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public InvalidInputException(Map<String, String> fieldErrors) {
        super("invalid input: " + fieldErrors.keySet());
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
