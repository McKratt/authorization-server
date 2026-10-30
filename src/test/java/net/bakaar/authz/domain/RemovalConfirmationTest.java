package net.bakaar.authz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RemovalConfirmationTest {

    private static final UserSummary ALICE = new UserSummary(UserId.parse("681472354872345601"), new Username("alice"));
    private static final UserSummary BOB = new UserSummary(UserId.parse("681472354872345602"), new Username("bob"));

    @Test
    void returnsTheOrphansWhenExactlyThoseAreConfirmed() {
        RemovalConfirmation confirmation = new RemovalConfirmation(List.of(ALICE, BOB), Set.of(BOB.id(), ALICE.id()));

        assertThat(confirmation.requireConfirmed()).containsExactlyInAnyOrder(ALICE.id(), BOB.id());
    }

    @Test
    void acceptsAChangeThatOrphansNobody() {
        assertThat(new RemovalConfirmation(List.of(), Set.of()).requireConfirmed()).isEmpty();
    }

    @Test
    void rejectsMissingConfirmations() {
        RemovalConfirmation confirmation = new RemovalConfirmation(List.of(ALICE, BOB), Set.of(ALICE.id()));

        assertThatThrownBy(confirmation::requireConfirmed)
                .isInstanceOfSatisfying(UnconfirmedRemovalException.class,
                        e -> assertThat(e.wouldRemove()).containsExactly(ALICE, BOB));
    }

    @Test
    void rejectsConfirmationsOfUsersThatAreNotOrphaned() {
        RemovalConfirmation confirmation = new RemovalConfirmation(List.of(ALICE), Set.of(ALICE.id(), BOB.id()));

        assertThatThrownBy(confirmation::requireConfirmed).isInstanceOf(UnconfirmedRemovalException.class);
    }

    @Test
    void rejectsConfirmationsWhenNobodyIsOrphaned() {
        RemovalConfirmation confirmation = new RemovalConfirmation(List.of(), Set.of(ALICE.id()));

        assertThatThrownBy(confirmation::requireConfirmed)
                .isInstanceOfSatisfying(UnconfirmedRemovalException.class, e -> assertThat(e.wouldRemove()).isEmpty());
    }
}
