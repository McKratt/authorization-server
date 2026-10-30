package net.bakaar.authz.adapter.in.web.admin.auth;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * Guards every {@code /admin} path except {@code /admin/login} and {@code /admin/static/**}; anything outside
 * {@code /admin} (the API, {@code /oidc/redirect}, health) passes through untouched.
 * <ol>
 *     <li>No valid session: htmx requests get 401 with {@code HX-Redirect}, others a 302, both to
 *     {@code /admin/login?returnTo=<path>}. For htmx the path is that of the page ({@code HX-Current-URL}), not
 *     of the fragment requested, so the user does not land on a bare dialog after signing in again.</li>
 *     <li>Neither admin role: 403, as a page with a sign-out button for a normal GET, empty otherwise. Only
 *     {@code POST /admin/logout} (with the CSRF token) gets through, so the user can switch accounts.</li>
 *     <li>Any method but GET without an {@code X-CSRF-Token} equal to the session's: 403.</li>
 *     <li>Any method but GET without {@code authz-admin}: 403, except {@code POST /admin/logout}.</li>
 * </ol>
 * Otherwise the {@link AdminSession} is registered in the request context for the handlers.
 */
public final class AdminAuthFilter implements Filter {

    static final HeaderName HX_REQUEST = HeaderNames.create("HX-Request");
    static final HeaderName HX_REDIRECT = HeaderNames.create("HX-Redirect");
    static final HeaderName HX_CURRENT_URL = HeaderNames.create("HX-Current-URL");
    static final HeaderName CSRF_TOKEN = HeaderNames.create("X-CSRF-Token");
    static final String LOGOUT_PATH = "/admin/logout";

    private final SessionCookies cookies;
    private final AuthPages pages;

    public AdminAuthFilter(SessionCookies cookies, AuthPages pages) {
        this.cookies = cookies;
        this.pages = pages;
    }

    /**
     * The session the filter admitted; only valid inside handlers of guarded paths.
     */
    public static AdminSession session(ServerRequest request) {
        return request.context().get(AdminSession.class)
                .orElseThrow(() -> new IllegalStateException("No admin session: path not guarded by AdminAuthFilter"));
    }

    @Override
    public void filter(FilterChain chain, RoutingRequest request, RoutingResponse response) {
        String path = request.path().path();
        if (!isGuarded(path)) {
            chain.proceed();
            return;
        }
        response.header(HeaderNames.CACHE_CONTROL, "no-store");

        Optional<AdminSession> found = cookies.session(request);
        if (found.isEmpty()) {
            redirectToLogin(request, response, path);
            return;
        }
        AdminSession session = found.get();
        Method method = request.prologue().method();
        boolean logout = Method.POST.equals(method) && LOGOUT_PATH.equals(path);
        if (!session.canRead() && !logout) {
            if (Method.GET.equals(method) && !isHtmx(request)) {
                pages.forbidden(request, response, session);
            } else {
                forbidden(response);
            }
            return;
        }
        if (!Method.GET.equals(method)) {
            if (!hasCsrfToken(request, session) || !(session.canWrite() || logout)) {
                forbidden(response);
                return;
            }
        }
        request.context().register(session);
        chain.proceed();
    }

    static boolean isGuarded(String path) {
        if (!path.equals("/admin") && !path.startsWith("/admin/")) {
            return false;
        }
        if (path.contains("/../") || path.endsWith("/..") || path.contains("/./") || path.contains("//")) {
            return true;
        }
        return !path.equals("/admin/login") && !path.startsWith("/admin/static/");
    }

    static boolean isHtmx(ServerRequest request) {
        return request.headers().contains(HX_REQUEST);
    }

    private static void redirectToLogin(ServerRequest request, ServerResponse response, String path) {
        Optional<String> returnTo = isHtmx(request) ? currentPagePath(request) : Optional.of(path);
        String login = "/admin/login?returnTo="
                + URLEncoder.encode(LoginRoutes.safeReturnTo(returnTo), StandardCharsets.UTF_8);
        if (isHtmx(request)) {
            response.header(HX_REDIRECT, login).status(Status.UNAUTHORIZED_401).send();
        } else {
            response.header(HeaderNames.LOCATION, login).status(Status.FOUND_302).send();
        }
    }

    /** Raw path of the page an htmx request came from; empty when the header is missing, repeated or no URI. */
    private static Optional<String> currentPagePath(ServerRequest request) {
        List<String> values = request.headers().all(HX_CURRENT_URL, List::of);
        if (values.size() != 1) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(new URI(values.getFirst()).getRawPath());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }

    private static boolean hasCsrfToken(ServerRequest request, AdminSession session) {
        List<String> values = request.headers().all(CSRF_TOKEN, List::of);
        return values.size() == 1 && MessageDigest.isEqual(
                values.getFirst().getBytes(StandardCharsets.UTF_8),
                session.csrfToken().getBytes(StandardCharsets.UTF_8));
    }

    private static void forbidden(ServerResponse response) {
        response.status(Status.FORBIDDEN_403).send();
    }
}
