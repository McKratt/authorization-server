package archfixtures.application.service;

/** Violation: a command outside port.in. */
public record MisplacedCommand(String value) {
}
