package net.bakaar.authz.adapter.in.web.admin;

import static net.bakaar.authz.adapter.in.web.admin.AdminPages.model;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import java.util.Set;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.port.in.GetApplication;

/**
 * Application routes, registered under {@code /admin/applications}. For now only the role checkboxes the new user
 * and grant dialogs load when an application is chosen.
 */
public final class ApplicationsRoutes implements HttpService {

    private final AdminPages pages;
    private final GetApplication getApplication;

    public ApplicationsRoutes(AdminPages pages, GetApplication getApplication) {
        this.pages = pages;
        this.getApplication = getApplication;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.get("/{appId}/role-options", this::roleOptions);
    }

    private void roleOptions(ServerRequest request, ServerResponse response) {
        ApplicationDetails application = getApplication.getApplication(
                ApplicationId.parse(request.path().pathParameters().get("appId")));
        pages.render(request, response, "users/role-options.jte", model(
                "application", application,
                "checked", Set.of()));
    }
}
