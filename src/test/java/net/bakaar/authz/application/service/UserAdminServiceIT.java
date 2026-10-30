package net.bakaar.authz.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.InvalidInputException;
import net.bakaar.authz.domain.NameConflictException;
import net.bakaar.authz.domain.NotFoundException;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UnconfirmedRemovalException;
import net.bakaar.authz.domain.UserAccess;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.UserSummary;
import net.bakaar.authz.domain.Username;
import net.bakaar.authz.adapter.out.persistence.AuthorizationPersistenceAdapterConfiguration;
import net.bakaar.authz.application.port.in.CreateUserCommand;
import net.bakaar.authz.application.port.in.GrantRolesCommand;
import net.bakaar.authz.application.port.in.RevokeRoleCommand;
import net.bakaar.authz.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class UserAdminServiceIT {

    private TestDatabase database;
    private AuthorizationPersistenceAdapterConfiguration persistence;
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        database = new TestDatabase();
        persistence = new AuthorizationPersistenceAdapterConfiguration(Config.just(ConfigSources.create(Map.of(
                "url", database.jdbcUrl(),
                "username", database.username(),
                "password", database.password()))));
        database.insertFixtures();
        service = new UserAdminService(persistence.transactionRunner(), persistence.userStore(),
                persistence.applicationStore(), persistence.grantStore());
    }

    @AfterEach
    void tearDown() {
        if (persistence != null) {
            persistence.close();
        }
        database.close();
    }

    @Test
    void createsUserWithRolesOfOneApplication() {
        UserId dave = service.createUser(new CreateUserCommand(
                new Username("dave"), application("billing"), Set.of(role("billing/viewer"))));

        UserAccess access = service.getUser(dave);

        assertThat(access.username()).isEqualTo(new Username("dave"));
        assertThat(roleNames(access)).containsExactly("billing:viewer");
    }

    @Test
    void createWithoutRoleIsInvalidAndCreatesNothing() {
        // the command rejects itself, so the use case is never reached
        assertThatThrownBy(() -> service.createUser(
                new CreateUserCommand(new Username("dave"), application("billing"), Set.of())))
                .isInstanceOfSatisfying(InvalidInputException.class, e ->
                        assertThat(e.fieldErrors()).containsEntry("roleId", "Select at least one role"));
        assertThat(service.listUsers()).extracting(u -> u.username().value()).doesNotContain("dave");
    }

    @Test
    void rejectsRolesOfAnotherApplication() {
        ApplicationId billing = application("billing");
        RoleId crmAdmin = role("crm/admin");
        UserId alice = user("alice");

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                new Username("dave"), billing, Set.of(crmAdmin))))
                .isInstanceOfSatisfying(InvalidInputException.class, e ->
                        assertThat(e.fieldErrors())
                                .containsEntry("roleId", "Roles must belong to the selected application"));
        assertThatThrownBy(() -> service.grantRoles(new GrantRolesCommand(alice, billing, Set.of(crmAdmin))))
                .isInstanceOf(InvalidInputException.class);

        assertThat(service.listUsers()).extracting(u -> u.username().value()).containsExactly("alice", "bob");
        assertThat(roleNames(service.getUser(alice)))
                .containsExactly("billing:admin", "billing:viewer", "crm:viewer");
    }

    @Test
    void unknownApplicationOrUserIsNotFound() {
        ApplicationId unknownApp = ApplicationId.next();
        UserId unknownUser = UserId.next();

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                new Username("dave"), unknownApp, Set.of(role("billing/viewer")))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.grantRoles(new GrantRolesCommand(
                unknownUser, application("billing"), Set.of(role("billing/viewer")))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getUser(unknownUser)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.renameUser(unknownUser, new Username("x")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteUser(unknownUser)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.revokeRole(new RevokeRoleCommand(unknownUser, role("crm/admin"), Set.of())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.usersRemovedByRevoking(unknownUser, role("crm/admin")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void grantIgnoresRolesAlreadyHeld() {
        UserId alice = user("alice");

        service.grantRoles(new GrantRolesCommand(alice, application("billing"),
                Set.of(role("billing/admin"), role("billing/viewer"))));
        service.grantRoles(new GrantRolesCommand(alice, application("crm"), Set.of(role("crm/admin"))));

        assertThat(roleNames(service.getUser(alice)))
                .containsExactly("billing:admin", "billing:viewer", "crm:admin", "crm:viewer");
    }

    @Test
    void revokeNonLastRoleKeepsUser() {
        UserId alice = user("alice");

        service.revokeRole(new RevokeRoleCommand(alice, role("crm/viewer"), Set.of()));

        assertThat(roleNames(service.getUser(alice))).containsExactly("billing:admin", "billing:viewer");
    }

    @Test
    void revokeUnheldRoleIsNotFound() {
        UserId alice = user("alice");
        RoleId crmAdmin = role("crm/admin");

        assertThatThrownBy(() -> service.revokeRole(new RevokeRoleCommand(alice, crmAdmin, Set.of())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.usersRemovedByRevoking(alice, crmAdmin))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void revokeLastRoleRequiresConfirmation() {
        UserId bob = user("bob");
        RoleId crmAdmin = role("crm/admin");

        assertThat(service.usersRemovedByRevoking(bob, crmAdmin)).extracting(UserSummary::username)
                .containsExactly(new Username("bob"));
        assertThatThrownBy(() -> service.revokeRole(new RevokeRoleCommand(bob, crmAdmin, Set.of())))
                .isInstanceOfSatisfying(UnconfirmedRemovalException.class, e ->
                        assertThat(e.wouldRemove()).extracting(UserSummary::id).containsExactly(bob));
        assertThat(exists(bob)).isTrue();

        service.revokeRole(new RevokeRoleCommand(bob, crmAdmin, Set.of(bob)));

        assertThat(exists(bob)).isFalse();
    }

    @Test
    void renameToExistingUsernameIsConflict() {
        UserId bob = user("bob");

        assertThatThrownBy(() -> service.renameUser(bob, new Username("alice")))
                .isInstanceOfSatisfying(NameConflictException.class, e ->
                        assertThat(e.field()).isEqualTo("username"));
    }

    @Test
    void renamesUser() {
        UserId bob = user("bob");

        service.renameUser(bob, new Username("robert"));

        assertThat(service.getUser(bob).username()).isEqualTo(new Username("robert"));
    }

    @Test
    void deleteUserRemovesGrants() {
        UserId bob = user("bob");

        service.deleteUser(bob);

        assertThat(exists(bob)).isFalse();
        assertThat(count("SELECT count(*) FROM user_role WHERE user_id = " + bob.value())).isZero();
    }

    @Test
    @Timeout(60)
    void concurrentRevokesOfTheSameUserSerialize() throws Exception {
        UserId alice = user("alice");
        RoleId crmViewer = role("crm/viewer");
        try (Connection revoker = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password())) {
            revoker.setAutoCommit(false);
            try (PreparedStatement lock = revoker.prepareStatement("SELECT id FROM app_user WHERE id = ? FOR UPDATE");
                 PreparedStatement revoke = revoker.prepareStatement(
                         "DELETE FROM user_role WHERE user_id = ? AND role_id IN (?, ?)")) {
                lock.setLong(1, alice.value());
                lock.execute();
                revoke.setLong(1, alice.value());
                revoke.setLong(2, role("billing/admin").value());
                revoke.setLong(3, role("billing/viewer").value());
                revoke.executeUpdate();
                // alice now only holds crm/viewer, uncommitted

                CompletableFuture<Void> revocation = CompletableFuture.runAsync(
                        () -> service.revokeRole(new RevokeRoleCommand(alice, crmViewer, Set.of())));
                awaitBlockedOnLock();
                assertThat(revocation).isNotDone();
                revoker.commit();

                assertThatThrownBy(() -> revocation.get(30, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(UnconfirmedRemovalException.class)
                        .cause().isInstanceOfSatisfying(UnconfirmedRemovalException.class, e ->
                                assertThat(e.wouldRemove()).extracting(UserSummary::id).containsExactly(alice));
            }
        }
        assertThat(exists(alice)).isTrue();
        assertThat(roleNames(service.getUser(alice))).containsExactly("crm:viewer");
    }

    @Test
    @Timeout(60)
    void createUserWaitsForAConcurrentRoleDeletion() throws Exception {
        RoleId crmAdmin = role("crm/admin");
        try (Connection deleter = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password())) {
            deleter.setAutoCommit(false);
            deleteRoleUncommitted(deleter, crmAdmin);

            CompletableFuture<UserId> creation = CompletableFuture.supplyAsync(() -> service.createUser(
                    new CreateUserCommand(new Username("victor"), application("crm"), Set.of(crmAdmin))));
            awaitBlockedOnLock();
            assertThat(creation).isNotDone();
            deleter.commit();

            assertThatThrownBy(() -> creation.get(30, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(InvalidInputException.class)
                    .cause().isInstanceOfSatisfying(InvalidInputException.class, e ->
                            assertThat(e.fieldErrors())
                                    .containsEntry("roleId", "Roles must belong to the selected application"));
        }
        assertThat(service.listUsers()).extracting(u -> u.username().value()).doesNotContain("victor");
    }

    @Test
    @Timeout(60)
    void grantWaitsForAConcurrentRoleDeletion() throws Exception {
        RoleId crmAdmin = role("crm/admin");
        UserId alice = user("alice");
        try (Connection deleter = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password())) {
            deleter.setAutoCommit(false);
            deleteRoleUncommitted(deleter, crmAdmin);

            CompletableFuture<Void> grant = CompletableFuture.runAsync(() -> service.grantRoles(
                    new GrantRolesCommand(alice, application("crm"), Set.of(crmAdmin))));
            awaitBlockedOnLock();
            assertThat(grant).isNotDone();
            deleter.commit();

            assertThatThrownBy(() -> grant.get(30, TimeUnit.SECONDS)).hasCauseInstanceOf(InvalidInputException.class);
        }
        assertThat(roleNames(service.getUser(alice)))
                .containsExactly("billing:admin", "billing:viewer", "crm:viewer");
    }

    /** What deleteRole holds before it commits: the role row locked and deleted. */
    private static void deleteRoleUncommitted(Connection connection, RoleId role) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM role WHERE id = ?")) {
            delete.setLong(1, role.value());
            delete.executeUpdate();
        }
    }

    private void awaitBlockedOnLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        try (Connection monitor = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password());
             PreparedStatement query = monitor.prepareStatement("SELECT count(*) FROM pg_stat_activity"
                     + " WHERE wait_event_type = 'Lock' AND datname = current_database()")) {
            while (System.nanoTime() < deadline) {
                try (ResultSet rs = query.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return;
                    }
                }
                Thread.sleep(50);
            }
        }
        throw new AssertionError("the use case never blocked on the lock");
    }

    private int count(String sql) {
        try (Connection connection = DriverManager.getConnection(
                database.jdbcUrl(), database.username(), database.password());
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new AssertionError(e);
        }
    }

    private boolean exists(UserId id) {
        return service.listUsers().stream().anyMatch(u -> u.id().equals(id));
    }

    private static List<String> roleNames(UserAccess access) {
        return access.applications().stream()
                .flatMap(a -> a.roles().stream().map(r -> a.name().value() + ":" + r.name().value()))
                .toList();
    }

    private ApplicationId application(String name) {
        return new ApplicationId(database.idOf("application", name));
    }

    private RoleId role(String appAndRole) {
        return new RoleId(database.idOf("role", appAndRole));
    }

    private UserId user(String username) {
        return new UserId(database.idOf("app_user", username));
    }
}
