package net.bakaar.authz.adapter.out.idp;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static net.bakaar.authz.support.TestIdentityProvider.AUDIENCE;
import static net.bakaar.authz.support.TestIdentityProvider.ISSUER;
import static net.bakaar.authz.support.TestIdentityProvider.KEY_ID;
import static net.bakaar.authz.support.TestIdentityProvider.validClaims;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import net.bakaar.authz.domain.AccessToken;
import net.bakaar.authz.domain.InvalidAccessTokenException;
import net.bakaar.authz.domain.Username;
import net.bakaar.authz.support.TestIdentityProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JwtAccessTokenVerifierIT {

    private static TestIdentityProvider idp;
    private static TestIdentityProvider attacker;
    private static JwtAccessTokenVerifier verifier;

    @BeforeAll
    static void setUp() {
        idp = new TestIdentityProvider();
        attacker = new TestIdentityProvider();
        verifier = new JwtAccessTokenVerifier(new JwtSettings(
                ISSUER, URI.create(idp.jwksUri()), Set.of(AUDIENCE), "preferred_username",
                Set.of(JWSAlgorithm.RS256), Duration.ofSeconds(30)));
    }

    @AfterAll
    static void tearDown() {
        idp.close();
        attacker.close();
    }

    @Test
    void acceptsValidTokenAndExtractsUsername() {
        assertThat(verifier.verify(new AccessToken(idp.tokenFor("alice")))).isEqualTo(new Username("alice"));
    }

    @Test
    void usesConfiguredUsernameClaimNotSubject() {
        String token = idp.sign(validClaims("alice").subject("bob").build());
        assertThat(verifier.verify(new AccessToken(token))).isEqualTo(new Username("alice"));
    }

    @Test
    void rejectsExpiredToken() {
        Instant past = Instant.now().minusSeconds(120);
        assertRejected(idp.sign(validClaims("alice").expirationTime(Date.from(past)).build()));
    }

    @Test
    void rejectsTokenWithoutExpiry() {
        assertRejected(idp.sign(validClaims("alice").expirationTime(null).build()));
    }

    @Test
    void rejectsTokenNotYetValid() {
        Instant future = Instant.now().plusSeconds(120);
        assertRejected(idp.sign(validClaims("alice").notBeforeTime(Date.from(future)).build()));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        assertRejected(idp.sign(validClaims("alice").issuer("https://evil.test").build()));
    }

    @Test
    void rejectsTokenWithoutIssuer() {
        assertRejected(idp.sign(validClaims("alice").issuer(null).build()));
    }

    @Test
    void rejectsTokenForAnotherAudience() {
        assertRejected(idp.sign(validClaims("alice").audience("another-service").build()));
    }

    @Test
    void rejectsTokenWithoutAudience() {
        assertRejected(idp.sign(validClaims("alice").audience((String) null).build()));
    }

    @Test
    void rejectsTokenWithoutUsernameClaim() {
        assertRejected(idp.sign(validClaims("alice").claim("preferred_username", null).build()));
    }

    @Test
    void rejectsTokenWithBlankUsernameClaim() {
        assertRejected(idp.sign(validClaims("alice").claim("preferred_username", " ").build()));
    }

    @Test
    void rejectsTokenWithNonStringUsernameClaim() {
        assertRejected(idp.sign(validClaims("alice").claim("preferred_username", 42).build()));
    }

    @Test
    void rejectsTokenSignedWithUnknownKeyReusingTrustedKeyId() {
        RSAKey forged = TestIdentityProvider.generateKey(KEY_ID);
        assertRejected(TestIdentityProvider.sign(validClaims("alice").build(), forged));
    }

    @Test
    void rejectsTokenPointingToAttackerJwksThroughJkuHeader() throws Exception {
        attacker.wireMock().stubFor(get("/jwks")
                .willReturn(okJson(new JWKSet(attacker.signingKey().toPublicJWK()).toString())));
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(attacker.signingKey().getKeyID())
                .jwkURL(URI.create(attacker.jwksUri()))
                .build();
        assertRejected(TestIdentityProvider.sign(header, validClaims("alice").build(), attacker.signingKey()));
    }

    @Test
    void rejectsTokenEmbeddingItsOwnKey() {
        RSAKey forged = TestIdentityProvider.generateKey("embedded");
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(forged.getKeyID())
                .jwk(forged.toPublicJWK())
                .build();
        assertRejected(TestIdentityProvider.sign(header, validClaims("alice").build(), forged));
    }

    @Test
    void rejectsUnsignedToken() {
        assertRejected(new PlainJWT(validClaims("alice").build()).serialize());
    }

    @Test
    void rejectsHmacTokenSignedWithThePublicKey() throws JOSEException {
        byte[] publicKey = idp.signingKey().toPublicJWK().toJSONString().getBytes(StandardCharsets.UTF_8);
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(KEY_ID).build(),
                validClaims("alice").build());
        jwt.sign(new MACSigner(publicKey));
        assertRejected(jwt.serialize());
    }

    @Test
    void rejectsTokenWhosePayloadWasSwappedToImpersonateAnotherUser() {
        String[] parts = idp.tokenFor("alice").split("\\.");
        JWTClaimsSet bob = validClaims("bob").build();
        String forged = parts[0] + "." + Base64URL.encode(bob.toString()) + "." + parts[2];
        assertRejected(forged);
    }

    @Test
    void rejectsGarbage() {
        assertRejected("not-a-jwt");
    }

    @Test
    void settingsRejectSymmetricAlgorithms() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JwtSettings(
                ISSUER, URI.create(idp.jwksUri()), Set.of(AUDIENCE), "preferred_username",
                Set.of(JWSAlgorithm.HS256), Duration.ofSeconds(30)));
    }

    @Test
    void settingsRequireAudience() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JwtSettings(
                ISSUER, URI.create(idp.jwksUri()), Set.of(), "preferred_username",
                Set.of(JWSAlgorithm.RS256), Duration.ofSeconds(30)));
    }

    @Test
    void settingsRejectPlainHttpJwksUriByDefault() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "issuer", ISSUER,
                "jwks-uri", "http://idp.test/jwks",
                "audiences", AUDIENCE)));
        assertThatIllegalArgumentException().isThrownBy(() -> JwtSettings.fromConfig(config));
    }

    @Test
    void settingsParseCommaSeparatedAudiences() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "issuer", ISSUER,
                "jwks-uri", "https://idp.test/jwks",
                "audiences", "a, b")));
        assertThat(JwtSettings.fromConfig(config).audiences()).containsExactlyInAnyOrder("a", "b");
    }

    private static void assertRejected(String token) {
        assertThatThrownBy(() -> verifier.verify(new AccessToken(token)))
                .isInstanceOf(InvalidAccessTokenException.class);
    }
}
