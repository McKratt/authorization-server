package net.bakaar.authz.adapter.out.persistence;

import java.sql.SQLException;
import java.util.Map;
import net.bakaar.authz.application.domain.model.NameConflictException;

/** Translates unique-constraint violations into {@link NameConflictException}. */
final class UniqueViolations {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final Map<String, String> FIELDS = Map.of(
            "app_user_username_key", "username",
            "application_name_key", "name",
            "uk_role_application_name", "roleName");

    private UniqueViolations() {
    }

    static RuntimeException translate(SQLException e) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            String field = FIELDS.get(constraint(e));
            if (field != null) {
                return new NameConflictException(field);
            }
        }
        return new PersistenceException("Database operation failed", e);
    }

    private static String constraint(SQLException e) {
        return e instanceof org.postgresql.util.PSQLException psql && psql.getServerErrorMessage() != null
                ? psql.getServerErrorMessage().getConstraint()
                : null;
    }
}
