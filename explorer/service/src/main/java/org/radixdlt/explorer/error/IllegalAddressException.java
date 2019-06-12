package org.radixdlt.explorer.error;

/**
 * Thrown to indicate that a paged request has been made with an illegal address
 * representation.
 */
public class IllegalAddressException extends IllegalArgumentException {

    /**
     * Constructs an {@code IllegalPageException} with no particular message.
     */
    public IllegalAddressException() {
        super();
    }

    /**
     * Constructs an {@code IllegalPageException} with given details info.
     */
    public IllegalAddressException(String message) {
        super(message);
    }

    /**
     * Constructs an {@code IllegalPageException} with given cause.
     */
    public IllegalAddressException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs an {@code IllegalPageException} with given details and cause.
     */
    public IllegalAddressException(String message, Throwable cause) {
        super(message, cause);
    }

}
