package net.bakaar.authz.domain;

import java.util.Objects;

public record RoleName(String value) {

    public RoleName {
        Objects.requireNonNull(value, "role name must not be null");
        if (!ApplicationName.VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "role name must be 1-100 characters of letters, digits, '.', '_' or '-'");
        }
    }
}
