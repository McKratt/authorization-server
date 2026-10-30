package net.bakaar.authz.adapter.in.web.admin;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts sessions and sign-in states into cookie values: a JWE ({@code dir}, {@code A256GCM}) carrying a claims
 * JSON. AES-GCM authenticates as well as encrypts, so a value that decrypts was written by this server.
 * <p>
 * A {@code kind} claim keeps the two apart: a login cookie never decodes as a session, nor the reverse.
 * Expiry is stored with second precision. {@code decode} answers empty for anything it cannot trust: a malformed or
 * tampered value, another key, another kind, or a past expiry.
 */
final class SessionCodec {

    private static final JWEHeader HEADER = new JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A256GCM);
    private static final String KIND = "kind";
    private static final String SESSION = "session";
    private static final String LOGIN = "login";

    private final SecretKey key;

    SessionCodec(byte[] key) {
        if (key == null || key.length != 32) {
            throw new IllegalArgumentException("The session key must be 32 bytes");
        }
        this.key = new SecretKeySpec(key.clone(), "AES");
    }

    public String encode(AdminSession session) {
        return encrypt(new JWTClaimsSet.Builder()
                .claim(KIND, SESSION)
                .subject(session.username())
                .claim("roles", session.roles().stream().map(AdminRole::claimValue).sorted().toList())
                .claim("csrf", session.csrfToken())
                .claim("idt", session.idToken())
                .expirationTime(Date.from(session.expiresAt()))
                .build());
    }

    public String encode(LoginState login) {
        return encrypt(new JWTClaimsSet.Builder()
                .claim(KIND, LOGIN)
                .claim("state", login.state())
                .claim("nonce", login.nonce())
                .claim("cv", login.codeVerifier())
                .claim("rt", login.returnTo())
                .expirationTime(Date.from(login.expiresAt()))
                .build());
    }

    public Optional<AdminSession> decode(String value) {
        return decode(value, SESSION, claims -> {
            Set<AdminRole> roles = EnumSet.noneOf(AdminRole.class);
            List<String> roleClaims = claims.getStringListClaim("roles");
            if (roleClaims != null) {
                roleClaims.forEach(role -> AdminRole.fromClaim(role).ifPresent(roles::add));
            }
            return new AdminSession(claims.getSubject(), roles, claims.getStringClaim("csrf"),
                    claims.getStringClaim("idt"), claims.getExpirationTime().toInstant());
        });
    }

    public Optional<LoginState> decodeLoginState(String value) {
        return decode(value, LOGIN, claims -> new LoginState(claims.getStringClaim("state"),
                claims.getStringClaim("nonce"), claims.getStringClaim("cv"), claims.getStringClaim("rt"),
                claims.getExpirationTime().toInstant()));
    }

    private String encrypt(JWTClaimsSet claims) {
        try {
            EncryptedJWT jwt = new EncryptedJWT(HEADER, claims);
            jwt.encrypt(new DirectEncrypter(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not encrypt the cookie value", e);
        }
    }

    /**
     * Decrypts, checks kind and expiry, then maps the claims. Any failure, including a missing claim, gives empty.
     */
    private <T> Optional<T> decode(String value, String kind, ClaimsMapper<T> mapper) {
        if (value == null || value.isEmpty()) {
            return Optional.empty();
        }
        try {
            EncryptedJWT jwt = EncryptedJWT.parse(value);
            JWEHeader header = jwt.getHeader();
            if (!JWEAlgorithm.DIR.equals(header.getAlgorithm())
                    || !EncryptionMethod.A256GCM.equals(header.getEncryptionMethod())
                    || header.getCompressionAlgorithm() != null) {
                return Optional.empty();
            }
            jwt.decrypt(new DirectDecrypter(key));
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            if (!kind.equals(claims.getStringClaim(KIND)) || expiry == null || !expiry.toInstant().isAfter(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(mapper.map(claims));
        } catch (ParseException | JOSEException | RuntimeException e) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    private interface ClaimsMapper<T> {
        T map(JWTClaimsSet claims) throws ParseException;
    }
}
