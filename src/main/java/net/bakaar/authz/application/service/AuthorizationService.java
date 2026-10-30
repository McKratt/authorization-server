package net.bakaar.authz.application.service;

import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.bakaar.authz.domain.Authorization;
import net.bakaar.authz.domain.Role;
import net.bakaar.authz.domain.Username;
import net.bakaar.authz.application.port.in.AuthorizationLookup;
import net.bakaar.authz.application.port.in.AuthorizationQuery;
import net.bakaar.authz.application.port.out.UserRolesFinder;
import net.bakaar.authz.application.port.out.AccessTokenVerifier;

@RequiredArgsConstructor
public class AuthorizationService implements AuthorizationLookup {

    private final AccessTokenVerifier accessTokenVerifier;
    private final UserRolesFinder userRolesFinder;

    @Override
    public Optional<Authorization> getAuthorization(AuthorizationQuery query) {
        Username username = accessTokenVerifier.verify(query.accessToken());
        Set<Role> roles = userRolesFinder.loadRoles(username, query.application());
        if (roles.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Authorization(username, query.application(), roles));
    }
}
