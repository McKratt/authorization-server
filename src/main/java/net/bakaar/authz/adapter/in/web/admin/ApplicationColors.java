package net.bakaar.authz.adapter.in.web.admin;

import net.bakaar.authz.domain.ApplicationId;

/**
 * Picks one of the eight application pastels ({@code app-0} … {@code app-7} in {@code admin.css}) from the
 * application ID, so an application keeps its color without storing one.
 */
public final class ApplicationColors {

    static final int PALETTE_SIZE = 8;

    private ApplicationColors() {
    }

    public static int indexOf(ApplicationId id) {
        return (int) Long.remainderUnsigned(id.value(), PALETTE_SIZE);
    }

    /** The CSS class of the application's color, e.g. {@code app-3}. */
    public static String cssClass(ApplicationId id) {
        return "app-" + indexOf(id);
    }
}
