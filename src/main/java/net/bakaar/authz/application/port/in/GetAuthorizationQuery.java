package net.bakaar.authz.application.port.in;

import java.util.Objects;
import net.bakaar.authz.application.domain.model.AccessToken;
import net.bakaar.authz.application.domain.model.ApplicationName;

public record GetAuthorizationQuery(AccessToken accessToken, ApplicationName application) {

    public GetAuthorizationQuery {
        Objects.requireNonNull(accessToken, "access token must not be null");
        Objects.requireNonNull(application, "application must not be null");
    }
}
