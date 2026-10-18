package net.bakaar.authz.support;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keycloak stand-in for the admin UI sign-in: WireMock serves discovery, the authorization endpoint, the token
 * endpoint, the JWKS and the end-session endpoint. ID tokens are signed with the {@link TestIdentityProvider} key.
 * <p>
 * The issuer is {@link #ISSUER}; the server reaches the endpoints through {@link #baseUrl()} (the
 * {@code admin.oidc.discovery-base-url}), like Keycloak's back channel in docker compose.
 */
public final class FakeKeycloak implements AutoCloseable {

    public static final String ISSUER = TestIdentityProvider.ISSUER;
    public static final String CLIENT_ID = "authz-admin-ui";
    /** At least 256 bits, so a test can also forge an HS256 ID token signed with it. */
    public static final String CLIENT_SECRET = "test-client-secret-0123456789abcdef";
    public static final String REDIRECT_URI = "http://localhost:8080/oidc/redirect";

    private final TestIdentityProvider idp = new TestIdentityProvider();

    public FakeKeycloak() {
        WireMockServer wireMock = idp.wireMock();
        String base = wireMock.baseUrl();
        wireMock.stubFor(get("/.well-known/openid-configuration").willReturn(okJson("""
                {
                  "issuer": "%s",
                  "authorization_endpoint": "%s/auth",
                  "token_endpoint": "%s/token",
                  "jwks_uri": "%s/jwks",
                  "end_session_endpoint": "%s/logout",
                  "id_token_signing_alg_values_supported": ["RS256"]
                }""".formatted(ISSUER, base, base, base, base))));
        wireMock.stubFor(get(urlPathEqualTo("/auth")).willReturn(ok("login page")));
        wireMock.stubFor(get(urlPathEqualTo("/logout")).willReturn(ok("logged out")));
        wireMock.stubFor(post("/token").willReturn(unauthorized()));
    }

    /**
     * Makes the JWKS answer only after this delay, like a slow Keycloak.
     */
    public void delayJwks(Duration delay) {
        wireMock().stubFor(get("/jwks").willReturn(okJson(new JWKSet(idp.signingKey().toPublicJWK()).toString())
                .withFixedDelay((int) delay.toMillis())));
    }

    public String baseUrl() {
        return idp.wireMock().baseUrl();
    }

    public WireMockServer wireMock() {
        return idp.wireMock();
    }

    /**
     * Query parameters of the latest request the browser sent to the authorization endpoint.
     */
    public Map<String, String> latestAuthorizationRequest() {
        List<LoggedRequest> requests = wireMock().findAll(getRequestedFor(urlPathEqualTo("/auth")));
        if (requests.isEmpty()) {
            throw new IllegalStateException("No request reached /auth yet");
        }
        return query(URI.create(requests.getLast().getAbsoluteUrl()));
    }

    /**
     * Claims of a valid ID token answering the latest authorization request; tests tweak them for invalid tokens.
     */
    public JWTClaimsSet.Builder idTokenClaims(String username, String... roles) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject("sub-" + username)
                .claim("azp", CLIENT_ID)
                .claim("nonce", latestAuthorizationRequest().get("nonce"))
                .claim("preferred_username", username)
                .claim("roles", Arrays.asList(roles))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    /**
     * Makes the token endpoint answer the next code exchange with a valid ID token for the user.
     */
    public String stubNextLogin(String username, String... roles) {
        return stubIdToken(idTokenClaims(username, roles).build());
    }

    /**
     * Makes the token endpoint answer with an ID token carrying exactly these claims, signed with the published key.
     * The stub only answers a client_secret_basic request for {@link #CLIENT_ID}.
     */
    public String stubIdToken(JWTClaimsSet claims) {
        return stubRawIdToken(idp.sign(claims));
    }

    /**
     * Makes the token endpoint answer with this serialized ID token as is, e.g. a forged one.
     */
    public String stubRawIdToken(String idToken) {
        String basic = Base64.getEncoder().encodeToString(
                (CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        wireMock().stubFor(post("/token")
                .withHeader("Authorization", equalTo("Basic " + basic))
                .willReturn(okJson("""
                        {"access_token":"ignored","token_type":"Bearer","expires_in":300,"id_token":"%s"}"""
                        .formatted(idToken))));
        return idToken;
    }

    public static Map<String, String> query(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(name, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    @Override
    public void close() {
        idp.close();
    }
}
