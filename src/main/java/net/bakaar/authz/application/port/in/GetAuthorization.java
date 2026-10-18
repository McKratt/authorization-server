package net.bakaar.authz.application.port.in;

import java.util.Optional;
import net.bakaar.authz.application.domain.model.Authorization;
import net.bakaar.authz.application.domain.model.InvalidAccessTokenException;

/**
 * Returns the roles of the token's owner for the requested application.
 * The user is always derived from the verified token, never supplied by the caller.
 */
public interface GetAuthorization {

    /**
     * @return the authorization, or empty when the user holds no role for the application
     * @throws InvalidAccessTokenException when the token cannot be trusted
     */
    Optional<Authorization> getAuthorization(GetAuthorizationQuery query);
}
