package net.bakaar.authz.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.ApplicationSummary;
import net.bakaar.authz.application.domain.model.Description;
import net.bakaar.authz.application.domain.model.Email;
import net.bakaar.authz.application.domain.model.NameConflictException;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.RoleName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcApplicationStoreTest {

    private StoreFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new StoreFixture();
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void findAllApplicationsCountsDistinctUsers() {
        List<ApplicationSummary> all = fixture.transactions.inTransaction(fixture.applications::findAll);

        ApplicationSummary billing = byName(all, "billing");
        ApplicationSummary crm = byName(all, "crm");
        assertThat(billing.userCount()).isEqualTo(1);
        assertThat(crm.userCount()).isEqualTo(2);
        assertThat(billing.roles()).extracting(r -> r.name().value()).containsExactly("admin", "viewer");
        assertThat(billing.responsible().value()).isEqualTo("billing@bakaar.net");
    }

    @Test
    void duplicateRoleNameInSameApplicationIsNameConflict() {
        ApplicationId billing = new ApplicationId(fixture.database.idOf("application", "billing"));
        ApplicationId crm = new ApplicationId(fixture.database.idOf("application", "crm"));
        NewRole auditor = new NewRole(new RoleName("auditor"), Description.EMPTY);
        fixture.transactions.run(() -> fixture.applications.insertRole(billing, RoleId.next(), auditor));

        assertThatThrownBy(() -> fixture.transactions.run(
                () -> fixture.applications.insertRole(billing, RoleId.next(), auditor)))
                .isInstanceOfSatisfying(NameConflictException.class, e -> assertThat(e.field()).isEqualTo("roleName"));
        fixture.transactions.run(() -> fixture.applications.insertRole(crm, RoleId.next(), auditor));

        assertThat(fixture.transactions.inTransaction(() -> fixture.applications.roleIds(crm))).hasSize(3);
    }

    @Test
    void duplicateApplicationNameIsNameConflict() {
        assertThatThrownBy(() -> fixture.transactions.run(() -> fixture.applications.insert(
                ApplicationId.next(), new ApplicationName("billing"), new Email("x@bakaar.net"), Description.EMPTY)))
                .isInstanceOfSatisfying(NameConflictException.class, e -> assertThat(e.field()).isEqualTo("name"));
    }

    @Test
    void storesBlankDescriptionAsNullAndReadsItBack() {
        ApplicationId id = ApplicationId.next();
        fixture.transactions.run(() -> {
            fixture.applications.insert(id, new ApplicationName("wiki"), new Email("wiki@bakaar.net"),
                    new Description("  Knowledge  "));
            fixture.applications.insertRole(id, RoleId.next(), new NewRole(new RoleName("editor"), Description.EMPTY));
        });

        ApplicationDetails details = fixture.transactions.inTransaction(() -> fixture.applications.find(id)).orElseThrow();

        assertThat(details.description().value()).isEqualTo("Knowledge");
        assertThat(details.roles()).singleElement().satisfies(r -> {
            assertThat(r.description()).isEqualTo(Description.EMPTY);
            assertThat(r.userCount()).isZero();
        });
        assertThat(fixture.transactions.inTransaction(() -> fixture.applications.update(
                id, new ApplicationName("wiki"), new Email("wiki@bakaar.net"), Description.EMPTY))).isTrue();
        assertThat(fixture.transactions.inTransaction(() -> fixture.applications.delete(id))).isTrue();
        assertThat(fixture.transactions.inTransaction(() -> fixture.applications.find(id))).isEmpty();
    }

    private static ApplicationSummary byName(List<ApplicationSummary> all, String name) {
        return all.stream().filter(a -> a.name().value().equals(name)).findFirst().orElseThrow();
    }
}
