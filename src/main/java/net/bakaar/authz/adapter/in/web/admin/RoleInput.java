package net.bakaar.authz.adapter.in.web.admin;

/**
 * A role as typed into the application dialog, kept when the dialog is re-rendered with errors.
 *
 * @param invalidName        the name is the one the {@code roleName} error is about
 * @param invalidDescription the description is the one the {@code roleDescription} error is about
 */
public record RoleInput(String name, String description, boolean invalidName, boolean invalidDescription) {

    public static final RoleInput EMPTY = new RoleInput("", "", false, false);
}
