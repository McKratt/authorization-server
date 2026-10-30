package net.bakaar.authz.adapter.in.web.admin.auth;

import io.helidon.http.SetCookie;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the two encrypted cookies of the admin UI.
 * <ul>
 *     <li>{@code AUTHZ_ADMIN_SESSION} ({@code Path=/}): the {@link AdminSession}.</li>
 *     <li>{@code AUTHZ_ADMIN_LOGIN} ({@code Path=/oidc}, 10 minutes): the {@link LoginState} of a sign-in.</li>
 * </ul>
 * Both are {@code HttpOnly; SameSite=Lax}, and {@code Secure} unless {@code admin.cookie-secure=false}.
 * A cookie sent more than once is ignored rather than guessed at.
 */
public final class SessionCookies {

    static final String SESSION = "AUTHZ_ADMIN_SESSION";
    static final String LOGIN = "AUTHZ_ADMIN_LOGIN";
    static final Duration LOGIN_DURATION = Duration.ofMinutes(10);
    private static final ZonedDateTime EXPIRED = Instant.EPOCH.atZone(ZoneOffset.UTC);

    private final SessionCodec codec;
    private final boolean secure;

    public SessionCookies(SessionCodec codec, boolean secure) {
        this.codec = codec;
        this.secure = secure;
    }

    public Optional<AdminSession> session(ServerRequest request) {
        return single(request, SESSION).flatMap(codec::decode);
    }

    public Optional<LoginState> loginState(ServerRequest request) {
        return single(request, LOGIN).flatMap(codec::decodeLoginState);
    }

    public void setSession(ServerResponse response, AdminSession session) {
        response.headers().addCookie(cookie(SESSION, codec.encode(session), "/")
                .maxAge(Duration.between(Instant.now(), session.expiresAt()))
                .build());
    }

    public void clearSession(ServerResponse response) {
        response.headers().addCookie(cookie(SESSION, "", "/").expires(EXPIRED).build());
    }

    public void setLoginState(ServerResponse response, LoginState login) {
        response.headers().addCookie(cookie(LOGIN, codec.encode(login), "/oidc").maxAge(LOGIN_DURATION).build());
    }

    public void clearLoginState(ServerResponse response) {
        response.headers().addCookie(cookie(LOGIN, "", "/oidc").expires(EXPIRED).build());
    }

    private SetCookie.Builder cookie(String name, String value, String path) {
        return SetCookie.builder(name, value)
                .path(path)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SetCookie.SameSite.LAX);
    }

    private static Optional<String> single(ServerRequest request, String name) {
        List<String> values = request.headers().cookies().all(name, List::of);
        return values.size() == 1 ? Optional.of(values.getFirst()) : Optional.empty();
    }
}
