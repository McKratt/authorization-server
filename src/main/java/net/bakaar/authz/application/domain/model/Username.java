package net.bakaar.authz.application.domain.model;

import java.util.Objects;

public record Username(String value) {

    private static final int MAX_LENGTH = 255;

    public Username {
        Objects.requireNonNull(value, "username must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("username must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
