package net.bakaar.authz.adapter.in.web.admin;

import io.helidon.http.Status;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.bakaar.authz.application.domain.model.InvalidInputException;
import net.bakaar.authz.application.domain.model.NameConflictException;
import net.bakaar.authz.application.domain.model.NotFoundException;

/**
 * Field messages of one dialog submission and the status its re-render answers with: 422 for invalid input,
 * 409 once a name conflict is recorded. Every field is parsed before the write, so the dialog shows all its
 * errors at once.
 */
final class FieldErrors {

    private final Map<String, String> errors = new LinkedHashMap<>();
    private Status status = Status.UNPROCESSABLE_CONTENT_422;

    /**
     * Builds a field's value. A value type rejecting the input ({@link IllegalArgumentException}), or an ID
     * parser rejecting a malformed ID ({@link NotFoundException}), records {@code message} for the field.
     *
     * @return the value, or null when it was rejected
     */
    <T> T parse(String field, String message, Supplier<T> parser) {
        try {
            return parser.get();
        } catch (IllegalArgumentException | NotFoundException e) {
            errors.putIfAbsent(field, message);
            return null;
        }
    }

    /** Records a message for a field, keeping an earlier one. */
    void add(String field, String message) {
        errors.putIfAbsent(field, message);
    }

    /**
     * Runs the write unless a field is already invalid. The domain's {@link InvalidInputException} adds its
     * field messages; a {@link NameConflictException} adds the message {@code conflicts} gives for its field
     * and turns the status into 409.
     *
     * @return true when the write ran and succeeded
     */
    boolean write(Runnable write, Map<String, String> conflicts) {
        if (!errors.isEmpty()) {
            return false;
        }
        try {
            write.run();
            return true;
        } catch (InvalidInputException e) {
            e.fieldErrors().forEach(this::add);
        } catch (NameConflictException e) {
            add(e.field(), conflicts.getOrDefault(e.field(), "This name is already taken."));
            status = Status.CONFLICT_409;
        }
        return false;
    }

    Map<String, String> asMap() {
        return errors;
    }

    Status status() {
        return status;
    }
}
