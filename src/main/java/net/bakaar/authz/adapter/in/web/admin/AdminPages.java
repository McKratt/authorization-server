package net.bakaar.authz.adapter.in.web.admin;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.util.Map;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminAuthFilter;

/**
 * Renders the precompiled jte templates of the admin UI with the headers every admin page carries.
 */
public final class AdminPages {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'";

    private static final HeaderName CONTENT_SECURITY_POLICY_HEADER = HeaderNames.create("Content-Security-Policy");
    private static final HeaderName HX_REQUEST = HeaderNames.create("HX-Request");
    private static final HeaderName HX_HISTORY_RESTORE_REQUEST = HeaderNames.create("HX-History-Restore-Request");

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
        StringOutput output = new StringOutput();
        templates.render(template, model, output);
        send(response, output);
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
