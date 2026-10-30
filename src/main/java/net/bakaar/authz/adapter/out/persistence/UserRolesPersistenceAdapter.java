package net.bakaar.authz.adapter.out.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import net.bakaar.authz.domain.ApplicationName;
import net.bakaar.authz.domain.Role;
import net.bakaar.authz.domain.Username;
import net.bakaar.authz.application.port.out.UserRolesFinder;

@RequiredArgsConstructor
class UserRolesPersistenceAdapter implements UserRolesFinder {

    private static final String SELECT_ROLES = """
            SELECT r.name
            FROM user_role ur
                     JOIN app_user u ON u.id = ur.user_id
                     JOIN role r ON r.id = ur.role_id
                     JOIN application a ON a.id = r.application_id
            WHERE u.username = ?
              AND a.name = ?
            ORDER BY r.name
            """;

    private final DataSource dataSource;

    @Override
    public Set<Role> loadRoles(Username username, ApplicationName application) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ROLES)) {
            statement.setString(1, username.value());
            statement.setString(2, application.value());
            try (ResultSet rs = statement.executeQuery()) {
                Set<Role> roles = new LinkedHashSet<>();
                while (rs.next()) {
                    roles.add(new Role(rs.getString(1)));
                }
                return roles;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to load roles of user for application " + application.value(), e);
        }
    }
}
