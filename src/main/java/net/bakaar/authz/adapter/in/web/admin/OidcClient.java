package net.bakaar.authz.adapter.in.web.admin;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webclient.api.ClientResponseTyped;
import io.helidon.webclient.api.WebClient;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Authorization code flow with PKCE against Keycloak, as a confidential client (client_secret_basic).
 * <p>
 * Discovery is fetched on first use and cached, so the server starts while Keycloak is unreachable; a failed
 * fetch is not cached and is retried on the next sign-in.
 * <p>
 * ID tokens are accepted only when signed with RS256 by a key published at the discovered {@code jwks_uri}, with the
 * exact issuer, the client in {@code aud} (and in {@code azp} when there are several audiences), an {@code exp} in
 * the future, the nonce of the sign-in, and a non-blank {@code preferred_username}.
 */
@RequiredArgsConstructor
class OidcClient {

    /** For every call to Keycloak, the JWKS fetch included (Nimbus would otherwise give up after 500 ms). */
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OidcSettings settings;
    private final WebClient http;
    private final ReentrantLock discoveryLock = new ReentrantLock();
    private volatile Discovery discovery;

    /**
     * Keycloak's authorization endpoint for this sign-in: code flow, {@code openid} scope, PKCE S256, state and nonce.
     */
    public URI authorizationUri(LoginState login) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", settings.clientId());
        params.put("redirect_uri", settings.redirectUri().toString());
        params.put("scope", "openid");
        params.put("state", login.state());
        params.put("nonce", login.nonce());
        params.put("code_challenge", s256(login.codeVerifier()));
        params.put("code_challenge_method", "S256");
        return withQuery(discovery().authorizationEndpoint(), params);
    }

    /**
     * Redeems the code at the token endpoint and verifies the ID token against the sign-in's nonce.
     *
     * @throws OidcException when the exchange fails or the ID token is not trusted
     */
    public AdminIdentity exchange(String code, LoginState login) {
        Discovery provider = discovery();
        String form = formEncode(Map.of(
                "grant_type", "authorization_code",
                "code", code,
                "redirect_uri", settings.redirectUri().toString(),
                "code_verifier", login.codeVerifier()));
        ClientResponseTyped<String> response = send(() -> http.post()
                .uri(provider.tokenEndpoint())
                .header(HeaderNames.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(HeaderNames.ACCEPT, "application/json")
                .header(HeaderNames.AUTHORIZATION, "Basic " + basicCredentials())
                .submit(form, String.class), "token endpoint");
        if (response.status().code() != Status.OK_200.code()) {
            throw new OidcException("Token endpoint answered " + response.status().code());
        }
        String idToken;
        try {
            idToken = JSONObjectUtils.getString(JSONObjectUtils.parse(response.entity()), "id_token");
        } catch (ParseException e) {
            throw new OidcException("Token endpoint answered an unreadable body");
        }
        if (idToken == null) {
            throw new OidcException("Token endpoint answered without an ID token");
        }
        return verify(idToken, login.nonce(), provider);
    }

    /**
     * Keycloak's end-session endpoint.
     *
     * @throws OidcException when discovery fails or the provider publishes none
     */
    public URI endSessionUri() {
        URI endSession = discovery().endSessionEndpoint();
        if (endSession == null) {
            throw new OidcException("The provider publishes no end_session_endpoint");
        }
        return endSession;
    }

    private AdminIdentity verify(String idToken, String nonce, Discovery provider) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, null));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, provider.keys()));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                settings.clientId(),
                new JWTClaimsSet.Builder().issuer(settings.issuer()).claim("nonce", nonce).build(),
                Set.of("exp", "preferred_username")));
        JWTClaimsSet claims;
        try {
            claims = processor.process(idToken, null);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new OidcException("ID token rejected");
        }
        try {
            if (claims.getAudience().size() > 1 && !settings.clientId().equals(claims.getStringClaim("azp"))) {
                throw new OidcException("ID token has several audiences but another azp");
            }
            String username = claims.getStringClaim("preferred_username");
            if (username == null || username.isBlank()) {
                throw new OidcException("ID token has a blank preferred_username");
            }
            List<String> roleClaims = claims.getStringListClaim("roles");
            Set<AdminRole> roles = EnumSet.noneOf(AdminRole.class);
            if (roleClaims != null) {
                roleClaims.forEach(role -> AdminRole.fromClaim(role).ifPresent(roles::add));
            }
            return new AdminIdentity(username, roles, idToken);
        } catch (ParseException e) {
            throw new OidcException("ID token has a malformed preferred_username, azp or roles claim");
        }
    }

    private Discovery discovery() {
        Discovery cached = discovery;
        if (cached != null) {
            return cached;
        }
        discoveryLock.lock();
        try {
            if (discovery == null) {
                discovery = fetchDiscovery();
            }
            return discovery;
        } finally {
            discoveryLock.unlock();
        }
    }

    private Discovery fetchDiscovery() {
        ClientResponseTyped<String> response = send(() -> http.get()
                .uri(settings.discoveryUri())
                .header(HeaderNames.ACCEPT, "application/json")
                .request(String.class), "discovery");
        if (response.status().code() != Status.OK_200.code()) {
            throw new OidcException("Discovery answered " + response.status().code());
        }
        try {
            Map<String, Object> document = JSONObjectUtils.parse(response.entity());
            if (!settings.issuer().equals(JSONObjectUtils.getString(document, "issuer"))) {
                throw new OidcException("Discovery announces another issuer than admin.oidc.issuer");
            }
            URI jwksUri = endpoint(document, "jwks_uri", true);
            return new Discovery(
                    endpoint(document, "authorization_endpoint", true),
                    endpoint(document, "token_endpoint", true),
                    endpoint(document, "end_session_endpoint", false),
                    JWKSourceBuilder.<SecurityContext>create(jwksUri.toURL(), new DefaultResourceRetriever(
                            (int) TIMEOUT.toMillis(), (int) TIMEOUT.toMillis())).build());
        } catch (ParseException | MalformedURLException | IllegalArgumentException e) {
            throw new OidcException("Discovery document is invalid");
        }
    }

    private URI endpoint(Map<String, Object> document, String name, boolean required) throws ParseException {
        URI uri = JSONObjectUtils.getURI(document, name);
        if (uri == null) {
            if (required) {
                throw new OidcException("Discovery document has no " + name);
            }
            return null;
        }
        settings.requireTrustedEndpoint(uri, name);
        return uri;
    }

    /**
     * WebClient does not throw on an error status, only when Keycloak cannot be reached or does not answer in time.
     */
    private static ClientResponseTyped<String> send(Supplier<ClientResponseTyped<String>> call, String what) {
        try {
            return call.get();
        } catch (RuntimeException e) {
            throw new OidcException("Could not reach the " + what + ": " + e.getClass().getSimpleName());
        }
    }

    /**
     * RFC 6749 section 2.3.1: id and secret are form-encoded before being joined.
     */
    private String basicCredentials() {
        String credentials = URLEncoder.encode(settings.clientId(), StandardCharsets.UTF_8) + ":"
                + URLEncoder.encode(settings.clientSecret(), StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    static String s256(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static URI withQuery(URI endpoint, Map<String, String> params) {
        String base = endpoint.toString();
        return URI.create(base + (endpoint.getRawQuery() == null ? "?" : "&") + formEncode(params));
    }

    private static String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private record Discovery(URI authorizationEndpoint,
                             URI tokenEndpoint,
                             URI endSessionEndpoint,
                             JWKSource<SecurityContext> keys) {
    }

    /**
     * Sign-in failed or Keycloak is unreachable. Messages never contain codes, tokens or secrets.
     */
    public static final class OidcException extends RuntimeException {

        public OidcException(String message) {
            super(message);
        }
    }
}
