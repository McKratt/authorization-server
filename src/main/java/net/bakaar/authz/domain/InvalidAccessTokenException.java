package net.bakaar.authz.domain;

/**
 * The access token is malformed, not signed by the trusted identity provider, expired, or not meant for us.
 */
public class InvalidAccessTokenException extends RuntimeException {

    public InvalidAccessTokenException(String message) {
        super(message);
    }

    public InvalidAccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
