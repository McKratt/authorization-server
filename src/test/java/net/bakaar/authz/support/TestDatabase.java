package net.bakaar.authz.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL in a container. Fixtures:
 * <ul>
 *     <li>alice: billing/admin, billing/viewer, crm/viewer</li>
 *     <li>bob: crm/admin</li>
 * </ul>
 */
public final class TestDatabase implements AutoCloseable {

    private final PostgreSQLContainer container = new PostgreSQLContainer("postgres:18-alpine");

    public TestDatabase() {
        container.start();
    }

    public String jdbcUrl() {
        return container.getJdbcUrl();
    }

    public String username() {
        return container.getUsername();
    }

    public String password() {
        return container.getPassword();
    }

    /**
     * Inserts the fixtures; the schema must already be migrated.
     */
    public void insertFixtures() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO app_user (username) VALUES ('alice'), ('bob');
                    INSERT INTO application (name) VALUES ('billing'), ('crm');
                    INSERT INTO role (application_id, name)
                    SELECT a.id, r.name
                    FROM application a
                             CROSS JOIN (VALUES ('admin'), ('viewer')) AS r(name);
                    INSERT INTO user_role (user_id, role_id)
                    SELECT u.id, r.id
                    FROM app_user u
                             JOIN role r ON TRUE
                             JOIN application a ON a.id = r.application_id
                    WHERE (u.username = 'alice' AND a.name = 'billing')
                       OR (u.username = 'alice' AND a.name = 'crm' AND r.name = 'viewer')
                       OR (u.username = 'bob' AND a.name = 'crm' AND r.name = 'admin');
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert fixtures", e);
        }
    }

    @Override
    public void close() {
        container.stop();
    }
}
