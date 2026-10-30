package net.bakaar.authz.domain;

import java.util.Objects;
import java.util.Optional;

public record Description(String value) {

    public static final Description EMPTY = new Description("");

    private static final int MAX_LENGTH = 2000;

    public Description {
        Objects.requireNonNull(value, "description must not be null");
        value = value.trim();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("description must not exceed " + MAX_LENGTH + " characters");
        }
    }

    /** The value to persist: empty when blank, so it is stored as NULL. */
    public Optional<String> stored() {
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
