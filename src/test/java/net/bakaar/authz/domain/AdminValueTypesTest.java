package net.bakaar.authz.application.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AdminValueTypesTest {

    @Test
    void idsRoundTripAsDecimalStrings() {
        assertThat(UserId.parse("681472354872345601").asString()).isEqualTo("681472354872345601");
        assertThat(ApplicationId.parse("681472354872345602").asString()).isEqualTo("681472354872345602");
        assertThat(RoleId.parse("681472354872345603").asString()).isEqualTo("681472354872345603");
    }

    @Test
    void malformedIdIsNotFound() {
        assertThatThrownBy(() -> UserId.parse("abc")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserId.parse("-1")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> UserId.parse("")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> ApplicationId.parse("0")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> RoleId.parse("x1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void nextGeneratesPositiveIds() {
        assertThat(UserId.next().value()).isPositive();
        assertThat(ApplicationId.next().value()).isPositive();
        assertThat(RoleId.next().value()).isPositive();
    }

    @Test
    void emailIsTrimmedAndChecked() {
        assertThat(new Email(" a@b.ch ").value()).isEqualTo("a@b.ch");
        assertThatThrownBy(() -> new Email("nope")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("a@b")).isInstanceOf(IllegalArgumentException.class);
        String tooLong = "a".repeat(255 - "@b.ch".length()) + "@b.ch";
        assertThat(tooLong).hasSize(255);
        assertThatThrownBy(() -> new Email(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roleNameFollowsApplicationNamePattern() {
        assertThat(new RoleName("viewer").value()).isEqualTo("viewer");
        assertThat(new RoleName("a.b-c_1").value()).isEqualTo("a.b-c_1");
        for (String bad : new String[] {"", "-x", "has space", "a".repeat(101)}) {
            assertThatThrownBy(() -> new RoleName(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void descriptionBlankIsStoredAsNull() {
        assertThat(new Description("  ").stored()).isEmpty();
        assertThat(Description.EMPTY.value()).isEmpty();
        assertThat(new Description(" text ").stored()).contains("text");
        assertThatThrownBy(() -> new Description("a".repeat(2001))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Description("a".repeat(2000)).value()).hasSize(2000);
    }

    @Test
    void usernameIsNotTrimmed() {
        assertThat(new Username(" alice ").value()).isEqualTo(" alice ");
    }

    @Test
    void exceptionsCarryTheirDetails() {
        assertThat(new NameConflictException("username").field()).isEqualTo("username");
        assertThat(new InvalidInputException(java.util.Map.of("name", "bad")).fieldErrors())
                .containsEntry("name", "bad");
        var summary = new UserSummary(UserId.next(), new Username("alice"));
        assertThat(new UnconfirmedRemovalException(java.util.List.of(summary)).wouldRemove()).containsExactly(summary);
        assertThat(new NotFoundException("x")).hasMessage("x");
    }
}
