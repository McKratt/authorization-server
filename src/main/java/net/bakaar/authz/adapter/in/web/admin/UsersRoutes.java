package net.bakaar.authz.adapter.in.web.admin;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.bakaar.authz.adapter.in.web.admin.auth.AdminAuthFilter;
import net.bakaar.authz.application.domain.model.UserAccess;
import net.bakaar.authz.application.port.in.ListUsers;

/**
 * Users tab, registered under {@code /admin}: one row per user and application, with the user's role chips.
 */
public final class UsersRoutes implements HttpService {

    private final AdminPages pages;
    private final ListUsers listUsers;

    public UsersRoutes(AdminPages pages, ListUsers listUsers) {
        this.pages = pages;
        this.listUsers = listUsers;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/", this::usersTab);
    }

    private void usersTab(ServerRequest request, ServerResponse response) {
        pages.renderTab(request, response, "users", "users/tab.jte", Map.of(
                "session", AdminAuthFilter.session(request),
                "users", listUsers.listUsers()));
    }

    /**
     * Lower-case text the client-side filter matches: the username, application names and role names.
     */
    public static String filterText(UserAccess user) {
        return Stream.concat(Stream.of(user.username().value()), user.applications().stream()
                        .flatMap(app -> Stream.concat(Stream.of(app.name().value()),
                                app.roles().stream().map(role -> role.name().value()))))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }
}
