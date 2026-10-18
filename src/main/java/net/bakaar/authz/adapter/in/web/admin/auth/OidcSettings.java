package net.bakaar.authz.adapter.in.web.admin.auth;

import io.helidon.config.Config;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Keycloak client settings of the admin UI ({@code admin.oidc.*}).
 *
 * @param issuer           exact {@code iss} of the ID tokens
 * @param clientId         confidential client of the admin UI
 * @param clientSecret     its secret; never logged ({@link #toString()} redacts it)
 * @param redirectUri      {@code .../oidc/redirect} as registered at Keycloak; its origin is also where Keycloak
 *                         sends the browser back after logout
 * @param discoveryBaseUrl where discovery is fetched from ({@code <it>/.well-known/openid-configuration}); the issuer
 *                         unless the server reaches Keycloak through another (internal) address
 * @param allowInsecure    allows http for the issuer, the discovery URL and the discovered back-channel endpoints
 */
public record OidcSettings(String issuer,
                           String clientId,
                           String clientSecret,
                           URI redirectUri,
                           URI discoveryBaseUrl,
                           boolean allowInsecure) {

    public OidcSettings {
        requireText(issuer, "admin.oidc.issuer");
        requireText(clientId, "admin.oidc.client-id");
        requireText(clientSecret, "admin.oidc.client-secret");
        Objects.requireNonNull(redirectUri, "admin.oidc.redirect-uri must be set");
        Objects.requireNonNull(discoveryBaseUrl, "admin.oidc.discovery-base-url must be set");
        if (!redirectUri.isAbsolute() || redirectUri.getHost() == null) {
            throw new IllegalArgumentException("admin.oidc.redirect-uri must be an absolute URL");
        }
        if (!allowInsecure) {
            requireHttps(URI.create(issuer), "admin.oidc.issuer");
            requireHttps(discoveryBaseUrl, "admin.oidc.discovery-base-url");
        }
    }

    /**
     * Empty when {@code admin.oidc.issuer} is not configured: the admin UI is then disabled.
     */
    public static Optional<OidcSettings> fromConfig(Config admin) {
        Config oidc = admin.get("oidc");
        Optional<String> issuer = oidc.get("issuer").asString().asOptional().filter(value -> !value.isBlank());
        if (issuer.isEmpty()) {
            return Optional.empty();
        }
        String discoveryBaseUrl = oidc.get("discovery-base-url").asString().asOptional()
                .filter(value -> !value.isBlank())
                .orElse(issuer.get());
        return Optional.of(new OidcSettings(
                issuer.get(),
                oidc.get("client-id").asString().orElse(null),
                oidc.get("client-secret").asString().orElse(null),
                oidc.get("redirect-uri").asString().map(URI::create).orElse(null),
                URI.create(discoveryBaseUrl),
                oidc.get("allow-insecure").asBoolean().orElse(false)));
    }

    public URI discoveryUri() {
        String base = discoveryBaseUrl.toString();
        return URI.create((base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + "/.well-known/openid-configuration");
    }

    /**
     * Scheme, host and port of the redirect URI, e.g. {@code https://authz.example.com}.
     */
    public String origin() {
        return redirectUri.getScheme() + "://" + redirectUri.getRawAuthority();
    }

    void requireTrustedEndpoint(URI endpoint, String name) {
        if (!allowInsecure) {
            requireHttps(endpoint, name);
        }
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must be set");
        }
    }

    private static void requireHttps(URI uri, String name) {
        if (!"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException(name + " must use https");
        }
    }

    @Override
    public String toString() {
        return "OidcSettings[issuer=" + issuer + ", clientId=" + clientId + ", clientSecret=***, redirectUri="
                + redirectUri + ", discoveryBaseUrl=" + discoveryBaseUrl + ", allowInsecure=" + allowInsecure + "]";
    }
}
