package net.bakaar.authz.support;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;

/**
 * Fake identity provider: WireMock publishes the JWKS, this class signs tokens with the matching private key.
 */
public final class TestIdentityProvider implements AutoCloseable {

    public static final String ISSUER = "https://idp.test";
    public static final String AUDIENCE = "authz-server";
    public static final String KEY_ID = "test-key";

    private final WireMockServer wireMock;
    private final RSAKey signingKey;

    public TestIdentityProvider() {
        signingKey = generateKey(KEY_ID);
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get("/jwks").willReturn(okJson(new JWKSet(signingKey.toPublicJWK()).toString())));
    }

    public String jwksUri() {
        return wireMock.baseUrl() + "/jwks";
    }

    public WireMockServer wireMock() {
        return wireMock;
    }

    public RSAKey signingKey() {
        return signingKey;
    }

    /**
     * Claims of a valid token for the user; tests tweak them to build invalid tokens.
     */
    public static JWTClaimsSet.Builder validClaims(String username) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject("sub-" + username)
                .claim("preferred_username", username)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    public String tokenFor(String username) {
        return sign(validClaims(username).build());
    }

    public String sign(JWTClaimsSet claims) {
        return sign(claims, signingKey);
    }

    public static String sign(JWTClaimsSet claims, RSAKey key) {
        return sign(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims, key);
    }

    public static String sign(JWSHeader header, JWTClaimsSet claims, RSAKey key) {
        try {
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    public static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        wireMock.stop();
    }
}
