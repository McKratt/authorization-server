package net.bakaar.authz.application.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.bakaar.authz.domain.ApplicationId;
import net.bakaar.authz.domain.ApplicationName;
import net.bakaar.authz.domain.Description;
import net.bakaar.authz.domain.Email;
import net.bakaar.authz.domain.InvalidInputException;
import net.bakaar.authz.domain.NewRole;
import net.bakaar.authz.domain.RoleId;
import net.bakaar.authz.domain.RoleName;
import net.bakaar.authz.domain.UserId;
import net.bakaar.authz.domain.Username;
import org.junit.jupiter.api.Test;

/** Commands reject missing values and copy their collections, so a caller cannot change them afterwards. */
class CommandsTest {

    private static final ApplicationId APPLICATION = ApplicationId.parse("681472354872345601");
    private static final RoleId ROLE = RoleId.parse("681472354872345602");
    private static final UserId USER = UserId.parse("681472354872345603");

    @Test
    void createUserCommandCopiesItsRoles() {
        Set<RoleId> roles = new HashSet<>(Set.of(ROLE));
        CreateUserCommand command = new CreateUserCommand(new Username("alice"), APPLICATION, roles);

        roles.clear();

        assertThat(command.roles()).containsExactly(ROLE);
        assertThatThrownBy(() -> command.roles().add(ROLE)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createUserCommandRequiresUsernameAndApplication() {
        assertThatNullPointerException().isThrownBy(() -> new CreateUserCommand(null, APPLICATION, Set.of(ROLE)));
        assertThatNullPointerException().isThrownBy(() -> new CreateUserCommand(new Username("alice"), null, Set.of(ROLE)));
    }

    @Test
    void grantRolesCommandCopiesItsRoles() {
        Set<RoleId> roles = new HashSet<>(Set.of(ROLE));
        GrantRolesCommand command = new GrantRolesCommand(USER, APPLICATION, roles);

        roles.clear();

        assertThat(command.roles()).containsExactly(ROLE);
        assertThatNullPointerException().isThrownBy(() -> new GrantRolesCommand(null, APPLICATION, Set.of()));
    }

    @Test
    void revokeRoleCommandCopiesItsConfirmations() {
        Set<UserId> confirmed = new HashSet<>(Set.of(USER));
        RevokeRoleCommand command = new RevokeRoleCommand(USER, ROLE, confirmed);

        confirmed.clear();

        assertThat(command.confirmedRemovals()).containsExactly(USER);
        assertThatNullPointerException().isThrownBy(() -> new RevokeRoleCommand(USER, null, Set.of()));
    }

    @Test
    void createApplicationCommandCopiesItsRoles() {
        List<NewRole> roles = new ArrayList<>(List.of(new NewRole(new RoleName("reader"), Description.EMPTY)));
        CreateApplicationCommand command = new CreateApplicationCommand(new ApplicationName("crm"),
                new Email("owner@example.com"), Description.EMPTY, roles);

        roles.clear();

        assertThat(command.roles()).hasSize(1);
        assertThatNullPointerException().isThrownBy(() -> new CreateApplicationCommand(new ApplicationName("crm"),
                null, Description.EMPTY, List.of()));
    }

    @Test
    void userCommandsRequireAtLeastOneRole() {
        assertThatThrownBy(() -> new CreateUserCommand(new Username("alice"), APPLICATION, Set.of()))
                .isInstanceOfSatisfying(InvalidInputException.class,
                        e -> assertThat(e.fieldErrors()).containsEntry("roleId", "Select at least one role"));
        assertThatThrownBy(() -> new GrantRolesCommand(USER, APPLICATION, Set.of()))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void createApplicationCommandRejectsDuplicateRoleNames() {
        NewRole reader = new NewRole(new RoleName("reader"), Description.EMPTY);

        assertThatThrownBy(() -> new CreateApplicationCommand(new ApplicationName("crm"),
                new Email("owner@example.com"), Description.EMPTY, List.of(reader, reader)))
                .isInstanceOfSatisfying(InvalidInputException.class,
                        e -> assertThat(e.fieldErrors()).containsEntry("roleName", "Role names must be unique"));
    }

    @Test
    void updateApplicationCommandRequiresAllValues() {
        assertThatNullPointerException().isThrownBy(() -> new UpdateApplicationCommand(APPLICATION,
                new ApplicationName("crm"), new Email("owner@example.com"), null));
    }
}
