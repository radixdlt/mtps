package org.radixdlt.explorer.validation;

/**
 * Describes a data validation interface.
 *
 * @param <INPUT_TYPE>  The type of data being validated.
 * @param <OUTPUT_TYPE> The type of the validated data.
 */
public interface Validator<INPUT_TYPE, OUTPUT_TYPE> {

    /**
     * Performs a validation of the given data.
     *
     * @param data The data to validate.
     * @return The data, if valid.
     * @throws IllegalArgumentException if the data is deemed invalid.
     */
    OUTPUT_TYPE validate(INPUT_TYPE data) throws RuntimeException;

}
