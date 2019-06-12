package org.radixdlt.explorer.validation;

import org.radixdlt.explorer.error.IllegalPageException;

import java.util.Optional;

/**
 * Offers a convenient way to validate a requested page value.
 */
public class PageValidator implements Validator<String, Integer> {

    @Override
    public Integer validate(String page) throws IllegalArgumentException {
        try {
            return Optional.of(Integer.parseInt(page, 10))
                    .filter(value -> value > 0)
                    .orElseThrow(IllegalArgumentException::new);
        } catch (Exception e) {
            throw new IllegalPageException("Illegal page: " + page, e);
        }
    }

}
