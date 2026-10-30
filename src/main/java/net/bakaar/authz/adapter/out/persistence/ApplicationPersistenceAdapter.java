package net.bakaar.authz.adapter.out.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.ApplicationSummary;
import net.bakaar.authz.application.domain.model.Description;
import net.bakaar.authz.application.domain.model.Email;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.RoleDetails;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.RoleName;
import net.bakaar.authz.application.domain.model.RoleSummary;
import net.bakaar.authz.application.port.out.ApplicationStore;

public class JdbcApplicationStore implements ApplicationStore {

    private final JdbcTransactions transactions;

    public JdbcApplicationStore(JdbcTransactions transactions) {
        this.transactions = transactions;
    }

    @Override
    public void insert(ApplicationId id, ApplicationName name, Email responsible, Description description) {
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "INSERT INTO application (id, name, responsible, description) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, id.value());
            statement.setString(2, name.value());
            statement.setString(3, responsible.value());
            statement.setObject(4, description.stored().orElse(null), Types.VARCHAR);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw UniqueViolations.translate(e);
        }
    }

    @Override
    public boolean update(ApplicationId id, ApplicationName name, Email responsible, Description description) {
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "UPDATE application SET name = ?, responsible = ?, description = ? WHERE id = ?")) {
            statement.setString(1, name.value());
            statement.setString(2, responsible.value());
            statement.setObject(3, description.stored().orElse(null), Types.VARCHAR);
            statement.setLong(4, id.value());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw UniqueViolations.translate(e);
        }
    }

    @Override
    public boolean delete(ApplicationId id) {
        try (PreparedStatement statement = transactions.current()
                .prepareStatement("DELETE FROM application WHERE id = ?")) {
            statement.setLong(1, id.value());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete application " + id.value(), e);
        }
    }

    @Override
    public Optional<ApplicationDetails> find(ApplicationId id) {
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "SELECT name, responsible, description FROM application WHERE id = ?")) {
            statement.setLong(1, id.value());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ApplicationDetails(id, new ApplicationName(rs.getString(1)),
                        new Email(rs.getString(2)), description(rs.getString(3)), roleDetails(id)));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load application " + id.value(), e);
        }
    }

    @Override
    public List<ApplicationSummary> findAll() {
        Map<Long, List<RoleSummary>> roles = new HashMap<>();
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "SELECT application_id, id, name FROM role ORDER BY name");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                roles.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(new RoleSummary(new RoleId(rs.getLong(2)), new RoleName(rs.getString(3))));
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load roles", e);
        }
        try (PreparedStatement statement = transactions.current().prepareStatement("""
                SELECT a.id, a.name, a.responsible, COUNT(DISTINCT ur.user_id)
                FROM application a
                         LEFT JOIN role r ON r.application_id = a.id
                         LEFT JOIN user_role ur ON ur.role_id = r.id
                GROUP BY a.id, a.name, a.responsible
                ORDER BY a.name
                """);
             ResultSet rs = statement.executeQuery()) {
            List<ApplicationSummary> applications = new ArrayList<>();
            while (rs.next()) {
                long id = rs.getLong(1);
                applications.add(new ApplicationSummary(new ApplicationId(id), new ApplicationName(rs.getString(2)),
                        new Email(rs.getString(3)), List.copyOf(roles.getOrDefault(id, List.of())), rs.getInt(4)));
            }
            return applications;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load applications", e);
        }
    }

    @Override
    public void insertRole(ApplicationId app, RoleId id, NewRole role) {
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "INSERT INTO role (id, application_id, name, description) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, id.value());
            statement.setLong(2, app.value());
            statement.setString(3, role.name().value());
            statement.setObject(4, role.description().stored().orElse(null), Types.VARCHAR);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw UniqueViolations.translate(e);
        }
    }

    @Override
    public boolean updateRole(ApplicationId app, RoleId id, NewRole role) {
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "UPDATE role SET name = ?, description = ? WHERE id = ? AND application_id = ?")) {
            statement.setString(1, role.name().value());
            statement.setObject(2, role.description().stored().orElse(null), Types.VARCHAR);
            statement.setLong(3, id.value());
            statement.setLong(4, app.value());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw UniqueViolations.translate(e);
        }
    }

    @Override
    public boolean deleteRole(ApplicationId app, RoleId id) {
        try (PreparedStatement statement = transactions.current()
                .prepareStatement("DELETE FROM role WHERE id = ? AND application_id = ?")) {
            statement.setLong(1, id.value());
            statement.setLong(2, app.value());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete role " + id.value(), e);
        }
    }

    @Override
    public Set<RoleId> roleIds(ApplicationId app) {
        try (PreparedStatement statement = transactions.current()
                .prepareStatement("SELECT id FROM role WHERE application_id = ?")) {
            statement.setLong(1, app.value());
            return roleIds(statement);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load roles of application " + app.value(), e);
        }
    }

    @Override
    public boolean lockAgainstDeletion(ApplicationId id) {
        return exists("SELECT id FROM application WHERE id = ? FOR KEY SHARE", id.value());
    }

    @Override
    public boolean lockForDeletion(ApplicationId id) {
        return exists("SELECT id FROM application WHERE id = ? FOR UPDATE", id.value());
    }

    @Override
    public Set<RoleId> lockRolesAgainstDeletion(ApplicationId app, Set<RoleId> roles) {
        if (roles.isEmpty()) {
            return Set.of();
        }
        Connection connection = transactions.current();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM role WHERE application_id = ? AND id = ANY(?) ORDER BY id FOR KEY SHARE")) {
            statement.setLong(1, app.value());
            statement.setArray(2, connection.createArrayOf("bigint",
                    roles.stream().map(RoleId::value).toArray(Long[]::new)));
            return roleIds(statement);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to lock roles of application " + app.value(), e);
        }
    }

    @Override
    public boolean lockRoleForDeletion(ApplicationId app, RoleId role) {
        return exists("SELECT id FROM role WHERE id = ? AND application_id = ? FOR UPDATE", role.value(), app.value());
    }

    @Override
    public Set<RoleId> lockRolesForDeletion(ApplicationId app) {
        try (PreparedStatement statement = transactions.current().prepareStatement(
                "SELECT id FROM role WHERE application_id = ? ORDER BY id FOR UPDATE")) {
            statement.setLong(1, app.value());
            return roleIds(statement);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to lock roles of application " + app.value(), e);
        }
    }

    private boolean exists(String sql, long... ids) {
        try (PreparedStatement statement = transactions.current().prepareStatement(sql)) {
            for (int i = 0; i < ids.length; i++) {
                statement.setLong(i + 1, ids[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to lock row", e);
        }
    }

    private static Set<RoleId> roleIds(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            Set<RoleId> ids = new LinkedHashSet<>();
            while (rs.next()) {
                ids.add(new RoleId(rs.getLong(1)));
            }
            return ids;
        }
    }

    private List<RoleDetails> roleDetails(ApplicationId app) throws SQLException {
        try (PreparedStatement statement = transactions.current().prepareStatement("""
                SELECT r.id, r.name, r.description, COUNT(ur.user_id)
                FROM role r
                         LEFT JOIN user_role ur ON ur.role_id = r.id
                WHERE r.application_id = ?
                GROUP BY r.id, r.name, r.description
                ORDER BY r.name
                """)) {
            statement.setLong(1, app.value());
            try (ResultSet rs = statement.executeQuery()) {
                List<RoleDetails> roles = new ArrayList<>();
                while (rs.next()) {
                    roles.add(new RoleDetails(new RoleId(rs.getLong(1)), new RoleName(rs.getString(2)),
                            description(rs.getString(3)), rs.getInt(4)));
                }
                return roles;
            }
        }
    }

    private static Description description(String stored) {
        return stored == null ? Description.EMPTY : new Description(stored);
    }
}
