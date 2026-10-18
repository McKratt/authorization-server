package net.bakaar.authz.adapter.in.web.admin;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.helidon.http.ForbiddenException;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.util.HashMap;
import java.util.Map;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminAuthFilter;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminSession;
import net.bakaar.authz.adapter.in.web.admin.auth.AuthPages;
import net.bakaar.authz.application.domain.model.NotFoundException;

/**
 * Renders the precompiled jte templates of the admin UI with the headers every admin page carries.
 */
public final class AdminPages implements AuthPages {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'";

    private static final HeaderName CONTENT_SECURITY_POLICY_HEADER = HeaderNames.create("Content-Security-Policy");
    private static final HeaderName HX_REQUEST = HeaderNames.create("HX-Request");
    private static final HeaderName HX_HISTORY_RESTORE_REQUEST = HeaderNames.create("HX-History-Restore-Request");
    private static final HeaderName HX_TRIGGER = HeaderNames.create("HX-Trigger");

    /** Reloads the open tab and closes the dialog; {@code authz:changed} first, while the form is still attached. */
    static final String CHANGED_TRIGGER = "{\"authz:changed\":true,\"authz:close-dialog\":true}";

    /** Reloads the open tab; the dialog stays open with the refreshed content. */
    static final String DIALOG_CHANGED_TRIGGER = "{\"authz:changed\":true}";

    private final TemplateEngine templates;

    public AdminPages(TemplateEngine templates) {
        this.templates = templates;
    }

    /**
     * True for htmx requests that want a fragment. A history restore (cache miss on back/forward) is an htmx
     * request too, but htmx swaps its response into the whole body, so it gets the full page.
     */
    public static boolean isHtmx(ServerRequest request) {
        return request.headers().contains(HX_REQUEST) && !request.headers().contains(HX_HISTORY_RESTORE_REQUEST);
    }

    /**
     * Renders one template on its own, e.g. a dialog.
     */
    public void render(ServerRequest request, ServerResponse response, String template, Map<String, Object> model) {
        render(request, response, Status.OK_200, template, model);
    }

    /**
     * Renders one template with a status, e.g. a dialog re-rendered with its errors (422 or 409).
     */
    public void render(ServerRequest request, ServerResponse response, Status status, String template,
                       Map<String, Object> model) {
        StringOutput output = new StringOutput();
        templates.render(template, model, output);
        response.status(status);
        send(response, output);
    }

    /**
     * A write inside a dialog succeeded and the dialog stays open: 200 with the refreshed dialog, and the open tab
     * reloads.
     */
    public void renderChanged(ServerRequest request, ServerResponse response, String template,
                              Map<String, Object> model) {
        response.header(HX_TRIGGER, DIALOG_CHANGED_TRIGGER);
        render(request, response, Status.OK_200, template, model);
    }

    /**
     * A dialog write succeeded: 204 without body; htmx closes the dialog and the open tab reloads itself.
     */
    public void changed(ServerResponse response) {
        response.status(Status.NO_CONTENT_204)
                .header(HX_TRIGGER, CHANGED_TRIGGER)
                .header(HeaderNames.CACHE_CONTROL, "no-store")
                .send();
    }

    @Override
    public void forbidden(ServerRequest request, ServerResponse response, AdminSession session) {
        render(request, response, Status.FORBIDDEN_403, "forbidden.jte", Map.of("session", session));
    }

    /**
     * Error handler for {@link NotFoundException} (unknown or malformed ID): 404 with no body.
     */
    public static void notFound(ServerRequest request, ServerResponse response, NotFoundException exception) {
        response.status(Status.NOT_FOUND_404)
                .header(HeaderNames.CACHE_CONTROL, "no-store")
                .send();
    }

    /**
     * For GET dialogs that only lead to writes: readers get 403. The filter already guards the writes themselves.
     */
    static void requireWriter(ServerRequest request) {
        if (!AdminAuthFilter.session(request).canWrite()) {
            throw new ForbiddenException("The authz-admin role is required");
        }
    }

    /**
     * A template model from key/value pairs; unlike {@link Map#of}, values may be null.
     */
    static Map<String, Object> model(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("model needs key/value pairs");
        }
        Map<String, Object> model = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            model.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return model;
    }

    /**
     * Renders a tab: inside the layout for a full page, or as the {@code #tab-content} fragment (plus the tab bar,
     * swapped out of band) for htmx.
     *
     * @param activeTab {@code users} or {@code applications}
     */
    public void renderTab(ServerRequest request, ServerResponse response, String activeTab, String template,
                          Map<String, Object> model) {
        Content content = output -> templates.render(template, model, output);
        StringOutput output = new StringOutput();
        if (isHtmx(request)) {
            templates.render("tabContent.jte", Map.of("activeTab", activeTab, "content", content, "oob", true),
                    output);
        } else {
            templates.render("layout.jte", Map.of("session", AdminAuthFilter.session(request),
                    "activeTab", activeTab, "content", content), output);
        }
        send(response, output);
    }

    private static void send(ServerResponse response, StringOutput output) {
        response.header(HeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8")
                .header(HeaderNames.CACHE_CONTROL, "no-store")
                .header(CONTENT_SECURITY_POLICY_HEADER, CONTENT_SECURITY_POLICY)
                .send(output.toString());
    }
}
