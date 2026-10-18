package net.bakaar.authz.adapter.out.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.application.port.out.GrantStore;

public class JdbcGrantStore implements GrantStore {

    private final JdbcTransactions transactions;

    public JdbcGrantStore(JdbcTransactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public void grant(UserId user, Set<RoleId> roles) {
        if (roles.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "INSERT INTO user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {
            for (RoleId role : roles) {
                statement.setLong(1, user.value());
                statement.setLong(2, role.value());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            throw new PersistenceException("Failed to grant roles to user " + user.value(), e);
        }
    }

    @Override
    public boolean revoke(UserId user, RoleId role) {
        try (PreparedStatement statement = transactions.current()
                .prepareStatement("DELETE FROM user_role WHERE user_id = ? AND role_id = ?")) {
            statement.setLong(1, user.value());
            statement.setLong(2, role.value());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to revoke role from user " + user.value(), e);
        }
    }

    @Override
    public List<UserSummary> usersLosingAllRoles(Set<RoleId> removed, Optional<UserId> onlyUser) {
        if (removed.isEmpty()) {
            return List.of();
        }
        Connection connection = transactions.current();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT u.id, u.username FROM app_user u
                WHERE (?::bigint IS NULL OR u.id = ?)
                  AND EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = ANY(?))
                  AND NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id <> ALL(?))
                ORDER BY u.username
                """)) {
            Long only = onlyUser.map(UserId::value).orElse(null);
            statement.setObject(1, only, Types.BIGINT);
            statement.setObject(2, only, Types.BIGINT);
            var ids = connection.createArrayOf("bigint", removed.stream().map(RoleId::value).toArray(Long[]::new));
            statement.setArray(3, ids);
            statement.setArray(4, ids);
            try (ResultSet rs = statement.executeQuery()) {
                List<UserSummary> users = new ArrayList<>();
                while (rs.next()) {
                    users.add(new UserSummary(new UserId(rs.getLong(1)), new Username(rs.getString(2))));
                }
                return users;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to compute users losing all roles", e);
        }
    }
}
