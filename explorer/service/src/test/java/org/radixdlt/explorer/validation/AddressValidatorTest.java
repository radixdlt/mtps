package org.radixdlt.explorer.validation;

import org.junit.Test;
import org.radixdlt.explorer.error.IllegalAddressException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AddressValidatorTest {

    @Test
    public void when_validating_known_valid_address__no_exception_is_thrown() {
        String address = "1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i";
        assertThatCode(() -> new AddressValidator().validate(address))
                .doesNotThrowAnyException();
    }

    @Test
    public void when_validating_known_invalid_address__exception_is_thrown() {
        String address = "1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62j";
        assertThatThrownBy(() -> new AddressValidator().validate(address))
                .isExactlyInstanceOf(IllegalAddressException.class);
    }

    @Test
    public void when_validating_empty_address__exception_is_thrown() {
        String address = "";
        assertThatThrownBy(() -> new AddressValidator().validate(address))
                .isExactlyInstanceOf(IllegalAddressException.class);
    }

    @Test
    public void when_validating_null_pointer_address__exception_is_thrown() {
        String address = null;
        assertThatThrownBy(() -> new AddressValidator().validate(address))
                .isExactlyInstanceOf(IllegalAddressException.class);
    }

    @Test
    public void when_validating_too_short_address__exception_is_thrown() {
        String address = "1AGNa15ZQXAZUgFiqJ2i7Z2DP";
        assertThatThrownBy(() -> new AddressValidator().validate(address))
                .isExactlyInstanceOf(IllegalAddressException.class);
    }

    @Test
    public void when_validating_too_long_address__exception_is_thrown() {
        String address = "1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62ijk";
        assertThatThrownBy(() -> new AddressValidator().validate(address))
                .isExactlyInstanceOf(IllegalAddressException.class);
    }

}
