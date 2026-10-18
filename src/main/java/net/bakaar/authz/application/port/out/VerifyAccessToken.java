package net.bakaar.authz.application.port.out;

import net.bakaar.authz.application.domain.model.AccessToken;
import net.bakaar.authz.application.domain.model.InvalidAccessTokenException;
import net.bakaar.authz.application.domain.model.Username;

/**
 * Verifies an access token against the trusted identity provider and extracts its owner.
 */
public interface VerifyAccessToken {

    /**
     * @return the username of the token's owner
     * @throws InvalidAccessTokenException when signature, issuer, audience, expiry or username claim is invalid
     */
    Username verify(AccessToken accessToken);
}
