package net.bakaar.authz.domain;

final class Parsing {

    private Parsing() {
    }

    static long positiveId(String text, String kind) {
        try {
            long id = Long.parseLong(text);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // falls through to NotFoundException
        }
        throw new NotFoundException(kind + " not found");
    }
}
