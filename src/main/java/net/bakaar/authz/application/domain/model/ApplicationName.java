package net.bakaar.authz.application.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record ApplicationName(String value) {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    public ApplicationName {
        Objects.requireNonNull(value, "application name must not be null");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "application name must be 1-100 characters of letters, digits, '.', '_' or '-'");
        }
    }
}
