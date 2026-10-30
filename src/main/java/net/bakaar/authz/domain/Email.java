package net.bakaar.authz.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern VALID = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        Objects.requireNonNull(value, "email must not be null");
        value = value.trim();
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("email must not exceed " + MAX_LENGTH + " characters");
        }
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("email must look like name@domain.tld");
        }
    }
}
