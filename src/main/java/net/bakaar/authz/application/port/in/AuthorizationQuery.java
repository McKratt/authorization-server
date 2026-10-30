package net.bakaar.authz.application.port.in;

import java.util.Objects;
import net.bakaar.authz.domain.AccessToken;
import net.bakaar.authz.domain.ApplicationName;

public record AuthorizationQuery(AccessToken accessToken, ApplicationName application) {

    public AuthorizationQuery {
        Objects.requireNonNull(accessToken, "access token must not be null");
        Objects.requireNonNull(application, "application must not be null");
    }
}
