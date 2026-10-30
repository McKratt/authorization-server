package net.bakaar.authz.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import net.bakaar.authz.domain.NameConflictException;
import net.bakaar.authz.domain.UserAccess;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.Username;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UserPersistenceAdapterIT {

    private static StoreFixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = new StoreFixture();
    }

    @AfterAll
    static void tearDown() {
        fixture.close();
    }

    @Test
    void findAllUsersGroupsRolesByApplication() {
        List<UserAccess> all = fixture.transactions.inTransaction(fixture.users::findAll);

        assertThat(all).extracting(u -> u.username().value()).startsWith("alice", "bob");
        assertThat(describe(all.get(0))).containsExactly("billing:[admin, viewer]", "crm:[viewer]");
        assertThat(describe(all.get(1))).containsExactly("crm:[admin]");
    }

    @Test
    void findReturnsOneUserOrEmpty() {
        UserId alice = new UserId(fixture.database.idOf("app_user", "alice"));

        Optional<UserAccess> found = fixture.transactions.inTransaction(() -> fixture.users.find(alice));
        Optional<UserAccess> missing = fixture.transactions.inTransaction(() -> fixture.users.find(UserId.next()));

        assertThat(found).get().extracting(u -> u.username().value()).isEqualTo("alice");
        assertThat(missing).isEmpty();
    }

    @Test
    void duplicateUsernameIsNameConflict() {
        assertThatThrownBy(() -> fixture.transactions.run(
                () -> fixture.users.insert(UserId.next(), new Username("alice"))))
                .isInstanceOfSatisfying(NameConflictException.class, e -> assertThat(e.field()).isEqualTo("username"));
    }

    @Test
    void rollbackOnException() {
        UserId id = UserId.next();

        assertThatThrownBy(() -> fixture.transactions.run(() -> {
            fixture.users.insert(id, new Username("ghost"));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fixture.transactions.inTransaction(() -> fixture.users.find(id))).isEmpty();
    }

    @Test
    void nestedTransactionsJoinTheOuterOne() {
        UserId id = UserId.next();

        assertThatThrownBy(() -> fixture.transactions.run(() -> {
            fixture.transactions.run(() -> fixture.users.insert(id, new Username("nested")));
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fixture.transactions.inTransaction(() -> fixture.users.find(id))).isEmpty();
    }

    @Test
    void currentConnectionIsUnavailableOutsideATransaction() {
        assertThatThrownBy(fixture.transactions::current).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void renameAndDelete() {
        UserId id = UserId.next();
        fixture.transactions.run(() -> fixture.users.insert(id, new Username("temp")));

        boolean renamed = fixture.transactions.inTransaction(() -> fixture.users.rename(id, new Username("temp2")));
        boolean deleted = fixture.transactions.inTransaction(() -> fixture.users.delete(id));
        boolean deletedAgain = fixture.transactions.inTransaction(() -> fixture.users.delete(id));

        assertThat(renamed).isTrue();
        assertThat(deleted).isTrue();
        assertThat(deletedAgain).isFalse();
    }

    private static List<String> describe(UserAccess user) {
        return user.applications().stream()
                .map(a -> a.name().value() + ":" + a.roles().stream().map(r -> r.name().value()).toList())
                .toList();
    }
}
