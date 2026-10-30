package net.bakaar.authz.domain;

import java.util.List;

/** The change would orphan users and the caller did not confirm exactly that set of users. */
public class UnconfirmedRemovalException extends RuntimeException {

    private final List<UserSummary> wouldRemove;

    public UnconfirmedRemovalException(List<UserSummary> wouldRemove) {
        super("change would remove " + wouldRemove.size() + " user(s)");
        this.wouldRemove = List.copyOf(wouldRemove);
    }

    public List<UserSummary> wouldRemove() {
        return wouldRemove;
    }
}
