package net.bakaar.authz.application.domain.model;

import java.util.Objects;

/**
 * Raw, not yet verified bearer token. {@link #toString()} is redacted so the token never ends up in logs.
 */
public record AccessToken(String value) {

    private static final int MAX_LENGTH = 16 * 1024;

    public AccessToken {
        Objects.requireNonNull(value, "access token must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("access token must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("access token must not exceed " + MAX_LENGTH + " characters");
        }
    }

    @Override
    public String toString() {
        return "AccessToken[***]";
    }
}
