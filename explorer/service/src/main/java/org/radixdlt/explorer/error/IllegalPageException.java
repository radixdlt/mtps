package org.radixdlt.explorer.error;

/**
 * Thrown to indicate that a paged request has been made with an illegal page
 * representation.
 */
public class IllegalPageException extends IllegalArgumentException {

    /**
     * Constructs an {@code IllegalPageException} with no particular message.
     */
    public IllegalPageException() {
        super();
    }

    /**
     * Constructs an {@code IllegalPageException} with given details info.
     */
    public IllegalPageException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code IllegalPageException} with given cause.
     */
    public IllegalPageException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs an {@code IllegalPageException} with given details and cause.
     */
    public IllegalPageException(String message, Throwable cause) {
        super(message, cause);
    }

}
