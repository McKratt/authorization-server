package net.bakaar.authz.application.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bakaar.authz.adapter.out.persistence.Database;
import net.bakaar.authz.adapter.out.persistence.JdbcApplicationStore;
import net.bakaar.authz.adapter.out.persistence.JdbcGrantStore;
import net.bakaar.authz.adapter.out.persistence.JdbcTransactions;
import net.bakaar.authz.adapter.out.persistence.JdbcUserStore;
import net.bakaar.authz.application.domain.model.ApplicationDetails;
import net.bakaar.authz.application.domain.model.ApplicationId;
import net.bakaar.authz.application.domain.model.ApplicationName;
import net.bakaar.authz.application.domain.model.Description;
import net.bakaar.authz.application.domain.model.Email;
import net.bakaar.authz.application.domain.model.InvalidInputException;
import net.bakaar.authz.application.domain.model.NameConflictException;
import net.bakaar.authz.application.domain.model.NewRole;
import net.bakaar.authz.application.domain.model.NotFoundException;
import net.bakaar.authz.application.domain.model.RoleDetails;
import net.bakaar.authz.application.domain.model.RoleId;
import net.bakaar.authz.application.domain.model.RoleName;
import net.bakaar.authz.application.domain.model.UnconfirmedRemovalException;
import net.bakaar.authz.application.domain.model.UserId;
import net.bakaar.authz.application.domain.model.UserSummary;
import net.bakaar.authz.application.domain.model.Username;
import net.bakaar.authz.application.port.in.CreateApplicationCommand;
import net.bakaar.authz.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationAdminServiceTest {

    private TestDatabase database;
    private HikariDataSource dataSource;
    private JdbcTransactions transactions;
    private JdbcUserStore users;
    private ApplicationAdminService service;

    @BeforeEach
    void setUp() {
        database = new TestDatabase();
        dataSource = Database.dataSource(Config.just(ConfigSources.create(Map.of(
                "url", database.jdbcUrl(),
                "username", database.username(),
                "password", database.password()))));
        Database.migrate(dataSource);
        database.insertFixtures();
        transactions = new JdbcTransactions(dataSource);
        users = new JdbcUserStore(transactions);
        service = new ApplicationAdminService(transactions, new JdbcApplicationStore(transactions),
                new JdbcGrantStore(transactions), users);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
        database.close();
    }

    @Test
    void createsApplicationWithRoles() {
        ApplicationId id = service.createApplication(new CreateApplicationCommand(
                new ApplicationName("wiki"), new Email("wiki@bakaar.net"), new Description("Knowledge base"),
                List.of(newRole("editor"), newRole("reader"))));

        ApplicationDetails details = service.getApplication(id);

        assertThat(details.name()).isEqualTo(new ApplicationName("wiki"));
        assertThat(details.responsible()).isEqualTo(new Email("wiki@bakaar.net"));
        assertThat(details.description()).isEqualTo(new Description("Knowledge base"));
        assertThat(details.roles()).extracting(RoleDetails::name, RoleDetails::userCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(new RoleName("editor"), 0),
                        org.assertj.core.groups.Tuple.tuple(new RoleName("reader"), 0));
    }

    @Test
    void createWithDuplicateRoleNamesIsInvalid() {
        var command = new CreateApplicationCommand(new ApplicationName("wiki"), new Email("wiki@bakaar.net"),
                Description.EMPTY, List.of(newRole("editor"), newRole("editor")));

        assertThatThrownBy(() -> service.createApplication(command))
                .isInstanceOfSatisfying(InvalidInputException.class, e ->
                        assertThat(e.fieldErrors()).containsEntry("roleName", "Role names must be unique"));
        assertThat(service.listApplications()).extracting(a -> a.name().value())
                .containsExactly("billing", "crm");
    }

    @Test
    void duplicateApplicationNameIsConflict() {
        var command = new CreateApplicationCommand(new ApplicationName("billing"), new Email("x@bakaar.net"),
                Description.EMPTY, List.of());

        assertThatThrownBy(() -> service.createApplication(command))
                .isInstanceOfSatisfying(NameConflictException.class, e -> assertThat(e.field()).isEqualTo("name"));
    }

    @Test
    void deleteRoleWithoutConfirmationOfOrphansIsRejected() {
        ApplicationId crm = application("crm");
        RoleId crmAdmin = role("crm/admin");

        assertThatThrownBy(() -> service.deleteRole(crm, crmAdmin, Set.of()))
                .isInstanceOfSatisfying(UnconfirmedRemovalException.class, e ->
                        assertThat(e.wouldRemove()).extracting(UserSummary::username)
                                .containsExactly(new Username("bob")));

        assertThat(exists(user("bob"))).isTrue();
        assertThat(service.getApplication(crm).roles()).extracting(RoleDetails::id).contains(crmAdmin);
    }

    @Test
    void deleteRoleWithConfirmationDeletesOrphans() {
        UserId bob = user("bob");
        service.deleteRole(application("crm"), role("crm/admin"), Set.of(bob));

        assertThat(exists(bob)).isFalse();
        assertThat(exists(user("alice"))).isTrue();
        assertThat(service.getApplication(application("crm")).roles()).extracting(r -> r.name().value())
                .containsExactly("viewer");
    }

    @Test
    void deleteApplicationDeletesOnlyUsersLeftWithoutRoles() {
        UserId bob = user("bob");
        service.deleteApplication(application("crm"), Set.of(bob));

        assertThat(exists(bob)).isFalse();
        assertThat(exists(user("alice"))).isTrue();
        assertThat(service.listApplications()).extracting(a -> a.name().value()).containsExactly("billing");
    }

    @Test
    void confirmingMoreUsersThanNeededIsRejected() {
        ApplicationId crm = application("crm");
        Set<UserId> tooMany = Set.of(user("alice"), user("bob"));

        assertThatThrownBy(() -> service.deleteApplication(crm, tooMany))
                .isInstanceOfSatisfying(UnconfirmedRemovalException.class, e ->
                        assertThat(e.wouldRemove()).extracting(UserSummary::username)
                                .containsExactly(new Username("bob")));

        assertThat(exists(user("alice"))).isTrue();
        assertThat(exists(user("bob"))).isTrue();
        assertThat(service.listApplications()).hasSize(2);
    }

    @Test
    void previewMatchesWhatDeleteWouldRemove() {
        assertThat(service.usersRemovedByDeletingApplication(application("crm")))
                .extracting(UserSummary::username).containsExactly(new Username("bob"));
        assertThat(service.usersRemovedByDeletingRole(application("crm"), role("crm/viewer"))).isEmpty();
    }

    @Test
    void roleOfAnotherApplicationIsNotFound() {
        ApplicationId billing = application("billing");
        RoleId crmAdmin = role("crm/admin");

        assertThatThrownBy(() -> service.deleteRole(billing, crmAdmin, Set.of(user("bob"))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.usersRemovedByDeletingRole(billing, crmAdmin))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateRole(billing, crmAdmin, newRole("x")))
                .isInstanceOf(NotFoundException.class);
        assertThat(exists(user("bob"))).isTrue();
    }

    @Test
    void unknownApplicationIsNotFound() {
        ApplicationId unknown = ApplicationId.next();

        assertThatThrownBy(() -> service.getApplication(unknown)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteApplication(unknown, Set.of())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.addRole(unknown, newRole("x"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesApplicationAndRole() {
        ApplicationId crm = application("crm");
        service.updateApplication(new net.bakaar.authz.application.port.in.UpdateApplicationCommand(
                crm, new ApplicationName("crm2"), new Email("c@bakaar.net"), new Description("d")));
        RoleId added = service.addRole(crm, newRole("auditor"));
        service.updateRole(crm, added, new NewRole(new RoleName("auditor2"), new Description("x")));

        ApplicationDetails details = service.getApplication(crm);
        assertThat(details.name()).isEqualTo(new ApplicationName("crm2"));
        assertThat(details.roles()).extracting(r -> r.name().value()).contains("auditor2");
    }

    private boolean exists(UserId id) {
        return transactions.inTransaction(() -> users.find(id)).isPresent();
    }

    private static NewRole newRole(String name) {
        return new NewRole(new RoleName(name), Description.EMPTY);
    }

    private ApplicationId application(String name) {
        return new ApplicationId(database.idOf("application", name));
    }

    private RoleId role(String appAndRole) {
        return new RoleId(database.idOf("role", appAndRole));
    }

    private UserId user(String username) {
        return new UserId(database.idOf("app_user", username));
    }
}
