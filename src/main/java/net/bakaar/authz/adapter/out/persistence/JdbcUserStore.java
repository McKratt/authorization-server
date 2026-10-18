package net.bakaar.authz.adapter.out.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.bakaar.authz.application.domain.model.ApplicationGrants;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.RoleName;
import net.bakaar.authz.application.domain.model.RoleSummary;
import net.bakaar.authz.application.domain.model.UserAccess;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.application.port.out.UserStore;

public class JdbcUserStore implements UserStore {

    private static final String SELECT_ACCESS = """
            SELECT u.id AS user_id, u.username, a.id AS app_id, a.name AS app_name, r.id AS role_id, r.name AS role_name
            FROM app_user u
                     LEFT JOIN user_role ur ON ur.user_id = u.id
                     LEFT JOIN role r ON r.id = ur.role_id
                     LEFT JOIN application a ON a.id = r.application_id
            %s
            ORDER BY u.username, a.name, r.name
            """;

    private final JdbcTransactions transactions;

    public JdbcUserStore(JdbcTransactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public void insert(UserId id, Username username) {
        try (PreparedStatement statement = transactions.current()
                .prepareStatement("INSERT INTO app_user (id, username) VALUES (?, ?)")) {
            statement.setLong(1, id.value());
            statement.setString(2, username.value());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw UniqueViolations.translate(e);
        }
    }

    @Override
    public boolean rename(UserId id, Username username) {
        try (PreparedStatement statement = transactions.current()
                .prepareStatement("UPDATE app_user SET username = ? WHERE id = ?")) {
            statement.setString(1, username.value());
            statement.setLong(2, id.value());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw UniqueViolations.translate(e);
        }
    }

    @Override
    public boolean delete(UserId id) {
        try (PreparedStatement statement = transactions.current()
                .prepareStatement("DELETE FROM app_user WHERE id = ?")) {
            statement.setLong(1, id.value());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete user " + id.value(), e);
        }
    }

    @Override
    public Optional<UserAccess> find(UserId id) {
        return query(SELECT_ACCESS.formatted("WHERE u.id = ?"), id.value()).stream().findFirst();
    }

    @Override
    public List<UserAccess> findAll() {
        return query(SELECT_ACCESS.formatted(""));
    }

    @Override
    public List<UserSummary> deleteUsersWithoutRoles(Collection<UserId> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Connection connection = transactions.current();
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM app_user u
                WHERE u.id = ANY(?)
                  AND NOT EXISTS (SELECT 1 FROM user_role WHERE user_id = u.id)
                RETURNING u.id, u.username
                """)) {
            statement.setArray(1, connection.createArrayOf("bigint",
                    candidates.stream().map(UserId::value).toArray(Long[]::new)));
            try (ResultSet rs = statement.executeQuery()) {
                List<UserSummary> deleted = new ArrayList<>();
                while (rs.next()) {
                    deleted.add(new UserSummary(new UserId(rs.getLong(1)), new Username(rs.getString(2))));
                }
                deleted.sort(Comparator.comparing(u -> u.username().value()));
                return deleted;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete users without roles", e);
        }
    }

    private List<UserAccess> query(String sql, Object... parameters) {
        try (PreparedStatement statement = transactions.current().prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return read(rs);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load users", e);
        }
    }

    /** Rows arrive ordered by user, application, role, so grouping is a single pass. */
    private static List<UserAccess> read(ResultSet rs) throws SQLException {
        List<UserAccess> users = new ArrayList<>();
        UserId userId = null;
        Username username = null;
        List<ApplicationGrants> applications = null;
        ApplicationId appId = null;
        ApplicationName appName = null;
        List<RoleSummary> roles = null;
        while (rs.next()) {
            UserId rowUser = new UserId(rs.getLong("user_id"));
            if (!rowUser.equals(userId)) {
                if (userId != null) {
                    closeApplication(applications, appId, appName, roles);
                    users.add(new UserAccess(userId, username, applications));
                }
                userId = rowUser;
                username = new Username(rs.getString("username"));
                applications = new ArrayList<>();
                appId = null;
                appName = null;
                roles = null;
            }
            long rowApp = rs.getLong("app_id");
            if (rs.wasNull()) {
                continue;
            }
            if (appId == null || appId.value() != rowApp) {
                closeApplication(applications, appId, appName, roles);
                appId = new ApplicationId(rowApp);
                appName = new ApplicationName(rs.getString("app_name"));
                roles = new ArrayList<>();
            }
            roles.add(new RoleSummary(new RoleId(rs.getLong("role_id")), new RoleName(rs.getString("role_name"))));
        }
        if (userId != null) {
            closeApplication(applications, appId, appName, roles);
            users.add(new UserAccess(userId, username, applications));
        }
        return users;
    }

    private static void closeApplication(List<ApplicationGrants> applications, ApplicationId id,
                                         ApplicationName name, List<RoleSummary> roles) {
        if (id != null) {
            applications.add(new ApplicationGrants(id, name, List.copyOf(roles)));
        }
    }
}
