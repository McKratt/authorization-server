package net.bakaar.authz.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import net.bakaar.authz.common.Ids;
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
        try (Connection connection = DriverManager.getConnection(jdbcUrl(), username(), password())) {
            long alice = insert(connection, "INSERT INTO app_user (id, username) VALUES (?, ?)", "alice");
            long bob = insert(connection, "INSERT INTO app_user (id, username) VALUES (?, ?)", "bob");
            long billing = insert(connection, "INSERT INTO application (id, name) VALUES (?, ?)", "billing");
            long crm = insert(connection, "INSERT INTO application (id, name) VALUES (?, ?)", "crm");
            long billingAdmin = insertRole(connection, billing, "admin");
            long billingViewer = insertRole(connection, billing, "viewer");
            long crmAdmin = insertRole(connection, crm, "admin");
            long crmViewer = insertRole(connection, crm, "viewer");
            grant(connection, alice, billingAdmin);
            grant(connection, alice, billingViewer);
            grant(connection, alice, crmViewer);
            grant(connection, bob, crmAdmin);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert fixtures", e);
        }
    }

    private static long insert(Connection connection, String sql, String name) throws SQLException {
        long id = Ids.next();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.setString(2, name);
            statement.executeUpdate();
        }
        return id;
    }

    private static long insertRole(Connection connection, long applicationId, String name) throws SQLException {
        long id = Ids.next();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO role (id, application_id, name) VALUES (?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setLong(2, applicationId);
            statement.setString(3, name);
            statement.executeUpdate();
        }
        return id;
    }

    private static void grant(Connection connection, long userId, long roleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO user_role (user_id, role_id) VALUES (?, ?)")) {
            statement.setLong(1, userId);
            statement.setLong(2, roleId);
            statement.executeUpdate();
        }
    }

    @Override
    public void close() {
        container.stop();
    }
}
