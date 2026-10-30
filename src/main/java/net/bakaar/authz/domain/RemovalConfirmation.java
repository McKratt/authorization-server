package net.bakaar.authz.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every user keeps at least one grant: a change that would leave users without any role deletes them, but only
 * when the caller confirmed exactly those users.
 *
 * @param orphans   the users the change would leave without any role, computed in the change's transaction
 * @param confirmed the users the caller agreed to delete
 */
public record RemovalConfirmation(List<UserSummary> orphans, Set<UserId> confirmed) {

    public RemovalConfirmation {
        orphans = List.copyOf(orphans);
        confirmed = Set.copyOf(confirmed);
    }

    /**
     * @return the ids of the users to delete along with the change
     * @throws UnconfirmedRemovalException unless the confirmed users are exactly the orphans
     */
    public Set<UserId> requireConfirmed() {
        Set<UserId> ids = orphans.stream().map(UserSummary::id).collect(Collectors.toUnmodifiableSet());
        if (!ids.equals(confirmed)) {
            throw new UnconfirmedRemovalException(orphans);
        }
        return ids;
    }
}
