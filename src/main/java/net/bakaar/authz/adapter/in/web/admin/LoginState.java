package net.bakaar.authz.adapter.in.web.admin.auth;

import java.time.Instant;
import java.util.Objects;

/**
 * State of a sign-in in progress, carried in the encrypted {@code AUTHZ_ADMIN_LOGIN} cookie between
 * {@code /admin/login} and {@code /oidc/redirect}.
 *
 * @param state        echoed back by Keycloak; must match the callback's {@code state} parameter
 * @param nonce        must come back in the ID token
 * @param codeVerifier PKCE verifier; only its S256 challenge goes to the browser
 * @param returnTo     local {@code /admin} path to land on after sign-in
 * @param expiresAt    end of the sign-in attempt
 */
public record LoginState(String state, String nonce, String codeVerifier, String returnTo, Instant expiresAt) {

    public LoginState {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(codeVerifier, "codeVerifier");
        Objects.requireNonNull(returnTo, "returnTo");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "LoginState[returnTo=" + returnTo + ", expiresAt=" + expiresAt + "]";
    }
}
