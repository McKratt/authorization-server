package net.bakaar.authz.application.domain.service;

import java.util.Optional;
import java.util.Set;
import net.bakaar.authz.application.domain.model.Authorization;
import net.bakaar.authz.application.domain.model.Role;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.application.port.in.GetAuthorization;
import net.bakaar.authz.application.port.in.GetAuthorizationQuery;
import net.bakaar.authz.application.port.out.LoadUserRoles;
import net.bakaar.authz.application.port.out.VerifyAccessToken;

public class AuthorizationService implements GetAuthorization {

    private final VerifyAccessToken verifyAccessToken;
    private final LoadUserRoles loadUserRoles;

    public AuthorizationService(VerifyAccessToken verifyAccessToken, LoadUserRoles loadUserRoles) {
        this.verifyAccessToken = verifyAccessToken;
        this.loadUserRoles = loadUserRoles;
    }

    @Override
    public Optional<Authorization> getAuthorization(GetAuthorizationQuery query) {
        Username username = verifyAccessToken.verify(query.accessToken());
        Set<Role> roles = loadUserRoles.loadRoles(username, query.application());
        if (roles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Authorization(username, query.application(), roles));
    }
}
