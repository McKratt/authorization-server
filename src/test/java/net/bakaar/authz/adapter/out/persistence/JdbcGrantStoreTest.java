package net.bakaar.authz.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcGrantStoreTest {

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
