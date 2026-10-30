package net.bakaar.authz.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import java.util.Map;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.Role;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.support.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UserRolesPersistenceAdapterTest {

    private static TestDatabase database;
    private static HikariDataSource dataSource;
    private static UserRolesPersistenceAdapter adapter;

    @BeforeAll
    static void setUp() {
        database = new TestDatabase();
        Config db = Config.just(ConfigSources.create(Map.of(
                "url", database.jdbcUrl(),
                "username", database.username(),
                "password", database.password())));
        dataSource = Database.dataSource(db);
        Database.migrate(dataSource);
        database.insertFixtures();
        adapter = new UserRolesPersistenceAdapter(dataSource);
    }

    @AfterAll
    static void tearDown() {
        dataSource.close();
        database.close();
    }

    @Test
    void returnsOnlyTheRolesOfTheRequestedApplication() {
        assertThat(adapter.loadRoles(new Username("alice"), new ApplicationName("billing")))
                .containsExactly(new Role("admin"), new Role("viewer"));
    }

    @Test
    void doesNotReturnRolesOfAnotherUserOnTheSameApplication() {
        assertThat(adapter.loadRoles(new Username("alice"), new ApplicationName("crm")))
                .containsExactly(new Role("viewer"));
    }

    @Test
    void returnsEmptyWhenUserHasNoGrantOnApplication() {
        assertThat(adapter.loadRoles(new Username("bob"), new ApplicationName("billing"))).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownUser() {
        assertThat(adapter.loadRoles(new Username("mallory"), new ApplicationName("billing"))).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownApplication() {
        assertThat(adapter.loadRoles(new Username("alice"), new ApplicationName("unknown"))).isEmpty();
    }

    @Test
    void usernameMatchIsExact() {
        assertThat(adapter.loadRoles(new Username("ALICE"), new ApplicationName("billing"))).isEmpty();
        assertThat(adapter.loadRoles(new Username("alice%"), new ApplicationName("billing"))).isEmpty();
    }
}
