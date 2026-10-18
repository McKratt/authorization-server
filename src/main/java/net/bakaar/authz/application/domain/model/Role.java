package net.bakaar.authz.application.domain.model;

import java.util.Objects;

public record Role(String name) {

    public Role {
        Objects.requireNonNull(name, "role name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("role name must not be blank");
        }
    }
}
