package net.bakaar.authz.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class GrantPersistenceAdapterIT {

    private StoreFixture fixture;
    private UserId alice;
    private UserId bob;
    private RoleId crmAdmin;
    private RoleId crmViewer;
    private RoleId billingViewer;

    @BeforeEach
    void setUp() {
        fixture = new StoreFixture();
        alice = new UserId(fixture.database.idOf("app_user", "alice"));
        bob = new UserId(fixture.database.idOf("app_user", "bob"));
        crmAdmin = new RoleId(fixture.database.idOf("role", "crm/admin"));
        crmViewer = new RoleId(fixture.database.idOf("role", "crm/viewer"));
        billingViewer = new RoleId(fixture.database.idOf("role", "billing/viewer"));
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void usersLosingAllRolesWhenDeletingCrmAdmin() {
        List<UserSummary> losing = fixture.transactions.inTransaction(
                () -> fixture.grants.usersLosingAllRoles(Set.of(crmAdmin), Optional.empty()));

        assertThat(losing).extracting(u -> u.username().value()).containsExactly("bob");
    }

    @Test
    void usersLosingAllRolesForOneUser() {
        assertThat(fixture.transactions.inTransaction(
                () -> fixture.grants.usersLosingAllRoles(Set.of(crmViewer), Optional.of(alice)))).isEmpty();
        assertThat(fixture.transactions.inTransaction(
                () -> fixture.grants.usersLosingAllRoles(Set.of(crmAdmin), Optional.of(bob))))
                .extracting(u -> u.username().value()).containsExactly("bob");
    }

    @Test
    void deletesOnlyUsersStillWithoutRolesAtCommit() {
        fixture.transactions.run(() -> fixture.grants.revoke(bob, crmAdmin));
        fixture.transactions.run(() -> fixture.grants.grant(bob, Set.of(billingViewer)));

        List<UserSummary> deleted = fixture.transactions.inTransaction(
                () -> fixture.users.deleteUsersWithoutRoles(List.of(bob)));

        assertThat(deleted).isEmpty();
        assertThat(fixture.transactions.inTransaction(() -> fixture.users.find(bob))).isPresent();
    }

    @Test
    @Timeout(60)
    void concurrentGrantSurvivesDeleteOfRolelessUser() throws Exception {
        try (Connection grantor = DriverManager.getConnection(
                fixture.database.jdbcUrl(), fixture.database.username(), fixture.database.password())) {
            grantor.setAutoCommit(false);
            try (PreparedStatement insert = grantor.prepareStatement(
                    "INSERT INTO user_role (user_id, role_id) VALUES (?, ?)")) {
                insert.setLong(1, bob.value());
                insert.setLong(2, billingViewer.value());
                insert.executeUpdate();
            }
            // grantor now holds a key-share lock on bob's row, uncommitted

            CompletableFuture<List<UserSummary>> deletion = CompletableFuture.supplyAsync(
                    () -> fixture.transactions.inTransaction(() -> {
                        fixture.grants.revoke(bob, crmAdmin);
                        return fixture.users.deleteUsersWithoutRoles(List.of(bob));
                    }));

            awaitBlockedOnLock();
            assertThat(deletion).isNotDone();
            grantor.commit();

            assertThat(deletion.get(30, TimeUnit.SECONDS)).isEmpty();
        }
        var bobAccess = fixture.transactions.inTransaction(() -> fixture.users.find(bob));
        assertThat(bobAccess).isPresent();
        assertThat(bobAccess.get().applications()).extracting(a -> a.name().value()).containsExactly("billing");
    }

    private void awaitBlockedOnLock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        try (Connection monitor = DriverManager.getConnection(
                fixture.database.jdbcUrl(), fixture.database.username(), fixture.database.password());
             PreparedStatement query = monitor.prepareStatement(
                     "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()")) {
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
        throw new AssertionError("deletion never blocked on the concurrent grant");
    }

    @Test
    void deletesUsersWithoutRoles() {
        fixture.transactions.run(() -> fixture.grants.revoke(bob, crmAdmin));

        List<UserSummary> deleted = fixture.transactions.inTransaction(
                () -> fixture.users.deleteUsersWithoutRoles(List.of(alice, bob)));

        assertThat(deleted).extracting(UserSummary::id).containsExactly(bob);
        assertThat(fixture.transactions.inTransaction(() -> fixture.users.find(bob))).isEmpty();
        assertThat(fixture.transactions.inTransaction(() -> fixture.users.find(alice))).isPresent();
    }

    @Test
    void grantIsIdempotentAndRevokeReportsChange() {
        fixture.transactions.run(() -> fixture.grants.grant(bob, Set.of(crmAdmin, crmViewer)));

        assertThat(fixture.transactions.inTransaction(() -> fixture.users.find(bob)).orElseThrow()
                .applications().get(0).roles()).hasSize(2);
        assertThat(fixture.transactions.inTransaction(() -> fixture.grants.revoke(bob, crmViewer))).isTrue();
        assertThat(fixture.transactions.inTransaction(() -> fixture.grants.revoke(bob, crmViewer))).isFalse();
    }
}
