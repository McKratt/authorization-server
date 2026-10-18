package net.bakaar.authz.adapter.out.idp;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Set;
import net.bakaar.authz.application.domain.model.AccessToken;
import net.bakaar.authz.application.domain.model.InvalidAccessTokenException;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.application.port.out.VerifyAccessToken;

/**
 * Verifies signed JWTs against the identity provider's JWKS.
 * <p>
 * Rejected: unsigned ({@code alg=none}) and encrypted tokens, algorithms outside the configured asymmetric set,
 * keys not published at the configured JWKS URI ({@code jku}/{@code x5u}/{@code jwk} headers are ignored),
 * a different issuer, none of the accepted audiences, a missing or past {@code exp}, a future {@code nbf},
 * and a missing, non-string or blank username claim.
 */
public class JwtAccessTokenVerifier implements VerifyAccessToken {

    private final ConfigurableJWTProcessor<SecurityContext> processor;
    private final String usernameClaim;

    public JwtAccessTokenVerifier(JwtSettings settings) {
        this(settings, remoteJwks(settings));
    }

    JwtAccessTokenVerifier(JwtSettings settings, JWKSource<SecurityContext> keys) {
        this.usernameClaim = settings.usernameClaim();
        this.processor = new DefaultJWTProcessor<>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                JOSEObjectType.JWT, new JOSEObjectType("at+jwt"), null));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(settings.algorithms(), keys));
        DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier = new DefaultJWTClaimsVerifier<>(
                settings.audiences(),
                new JWTClaimsSet.Builder().issuer(settings.issuer()).build(),
                Set.of("exp", usernameClaim),
                null);
        claimsVerifier.setMaxClockSkew(Math.toIntExact(settings.clockSkew().toSeconds()));
        processor.setJWTClaimsSetVerifier(claimsVerifier);
    }

    @Override
    public Username verify(AccessToken accessToken) {
        JWTClaimsSet claims;
        try {
            claims = processor.process(accessToken.value(), null);
        } catch (ParseException | BadJOSEException e) {
            throw new InvalidAccessTokenException("Access token rejected: " + e.getMessage(), e);
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new InvalidAccessTokenException("Access token could not be verified: " + e.getMessage(), e);
        }
        try {
            String username = claims.getStringClaim(usernameClaim);
            if (username == null || username.isBlank()) {
                throw new InvalidAccessTokenException("Access token has no " + usernameClaim + " claim");
            }
            return new Username(username);
        } catch (ParseException | IllegalArgumentException e) {
            throw new InvalidAccessTokenException("Access token has an invalid " + usernameClaim + " claim", e);
        }
    }

    private static JWKSource<SecurityContext> remoteJwks(JwtSettings settings) {
        try {
            return JWKSourceBuilder.<SecurityContext>create(settings.jwksUri().toURL()).build();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("jwt.jwks-uri is not a valid URL", e);
        }
    }
}
