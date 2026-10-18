package net.bakaar.authz.adapter.out.idp;

import com.nimbusds.jose.JWSAlgorithm;
import io.helidon.config.Config;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trust settings for access tokens. Issuer, JWKS URI and audiences are mandatory: there is no safe default.
 *
 * @param issuer        exact {@code iss} value of the trusted identity provider
 * @param jwksUri       where the provider publishes its public keys; never taken from the token itself
 * @param audiences     accepted {@code aud} values; the token must carry at least one of them
 * @param usernameClaim claim holding the username; it must be unique and immutable at the provider
 * @param algorithms    accepted signature algorithms; asymmetric only
 * @param clockSkew     tolerance for {@code exp} and {@code nbf}
 */
public record JwtSettings(String issuer,
                          URI jwksUri,
                          Set<String> audiences,
                          String usernameClaim,
                          Set<JWSAlgorithm> algorithms,
                          Duration clockSkew) {

    private static final Set<JWSAlgorithm> ASYMMETRIC = Set.copyOf(
            JWSAlgorithm.Family.SIGNATURE.stream().toList());

    public JwtSettings {
        Objects.requireNonNull(issuer, "jwt.issuer must be set");
        Objects.requireNonNull(jwksUri, "jwt.jwks-uri must be set");
        Objects.requireNonNull(usernameClaim, "jwt.username-claim must be set");
        Objects.requireNonNull(clockSkew, "jwt.clock-skew must be set");
        audiences = Set.copyOf(audiences);
        algorithms = Set.copyOf(algorithms);
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("jwt.issuer must not be blank");
        }
        if (audiences.isEmpty() || audiences.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("jwt.audiences must contain at least one non-blank value");
        }
        if (algorithms.isEmpty() || !ASYMMETRIC.containsAll(algorithms)) {
            throw new IllegalArgumentException("jwt.algorithms must only contain asymmetric (RS*, PS*, ES*, EdDSA) algorithms");
        }
    }

    public static JwtSettings fromConfig(Config jwt) {
        URI jwksUri = URI.create(jwt.get("jwks-uri").asString().get());
        boolean allowInsecure = jwt.get("allow-insecure-jwks-uri").asBoolean().orElse(false);
        if (!"https".equals(jwksUri.getScheme()) && !allowInsecure) {
            throw new IllegalArgumentException("jwt.jwks-uri must use https");
        }
        Set<JWSAlgorithm> algorithms = list(jwt.get("algorithms")).stream()
                .map(JWSAlgorithm::parse)
                .collect(Collectors.toSet());
        return new JwtSettings(
                jwt.get("issuer").asString().get(),
                jwksUri,
                Set.copyOf(list(jwt.get("audiences"))),
                jwt.get("username-claim").asString().orElse("preferred_username"),
                algorithms.isEmpty() ? Set.of(JWSAlgorithm.RS256) : algorithms,
                jwt.get("clock-skew").as(Duration.class).orElse(Duration.ofSeconds(30)));
    }

    /**
     * Accepts a YAML list as well as a comma-separated value (as given through env vars or system properties).
     */
    private static List<String> list(Config node) {
        if (node.isLeaf()) {
            return node.asString()
                    .map(value -> Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())
                    .orElse(List.of());
        }
        return node.asList(String.class).orElse(List.of());
    }
}
