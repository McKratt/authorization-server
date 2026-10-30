package net.bakaar.authz.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import net.bakaar.authz.domain.AccessToken;
import net.bakaar.authz.domain.ApplicationName;
import net.bakaar.authz.domain.Authorization;
import net.bakaar.authz.domain.InvalidAccessTokenException;
import net.bakaar.authz.domain.Role;
import net.bakaar.authz.domain.Username;
import net.bakaar.authz.application.port.in.AuthorizationQuery;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTest {

    private static final ApplicationName BILLING = new ApplicationName("billing");

    @Test
    void looksUpRolesOfTheTokenOwner() {
        AuthorizationService service = new AuthorizationService(
                token -> new Username("alice"),
                (username, application) -> username.equals(new Username("alice")) && application.equals(BILLING)
                        ? Set.of(new Role("admin"))
                        : Set.of());

        assertThat(service.getAuthorization(new AuthorizationQuery(new AccessToken("t"), BILLING)))
                .contains(new Authorization(new Username("alice"), BILLING, Set.of(new Role("admin"))));
    }

    @Test
    void returnsEmptyWhenUserHasNoRole() {
        AuthorizationService service = new AuthorizationService(token -> new Username("alice"), (u, a) -> Set.of());

        assertThat(service.getAuthorization(new AuthorizationQuery(new AccessToken("t"), BILLING))).isEmpty();
    }

    @Test
    void doesNotTouchTheDatabaseWhenTokenIsInvalid() {
        AuthorizationService service = new AuthorizationService(
                token -> {
                    throw new InvalidAccessTokenException("bad");
                },
                (u, a) -> {
                    throw new AssertionError("roles must not be loaded for an invalid token");
                });

        assertThatThrownBy(() -> service.getAuthorization(new AuthorizationQuery(new AccessToken("t"), BILLING)))
                .isInstanceOf(InvalidAccessTokenException.class);
    }

    @Test
    void accessTokenIsRedactedInToString() {
        assertThat(new AccessToken("secret-token")).hasToString("AccessToken[***]");
    }
}
