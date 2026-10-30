package net.bakaar.authz.application.port.in;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.bakaar.authz.domain.ApplicationName;
import net.bakaar.authz.domain.Description;
import net.bakaar.authz.domain.Email;
import net.bakaar.authz.domain.InvalidInputException;
import net.bakaar.authz.domain.NewRole;

public record CreateApplicationCommand(ApplicationName name, Email responsible, Description description,
                                       List<NewRole> roles) {

    public CreateApplicationCommand {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(responsible, "responsible must not be null");
        Objects.requireNonNull(description, "description must not be null");
        roles = List.copyOf(roles);
        Set<String> names = new HashSet<>();
        for (NewRole role : roles) {
            if (!names.add(role.name().value())) {
                throw new InvalidInputException(Map.of("roleName", "Role names must be unique"));
            }
        }
    }
}
