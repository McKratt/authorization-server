package net.bakaar.authz.application.domain.model;

/** A unique name is already taken; {@code field} is "username", "name" or "roleName". */
public class NameConflictException extends RuntimeException {

    private final String field;

    public NameConflictException(String field) {
        super(field + " already exists");
        this.field = field;
    }

    public String field() {
        return field;
    }
}
