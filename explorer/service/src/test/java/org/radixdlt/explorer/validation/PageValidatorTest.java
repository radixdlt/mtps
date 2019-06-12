package org.radixdlt.explorer.validation;

import org.junit.Test;
import org.radixdlt.explorer.error.IllegalPageException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PageValidatorTest {

    @Test
    public void when_validating_non_negative_numeric_page__no_exception_is_thrown() {
        assertThatCode(() -> new PageValidator().validate("2"))
                .doesNotThrowAnyException();
    }

    @Test
    public void when_validating_non_numeric_page__exception_is_thrown() {
        assertThatThrownBy(() -> new PageValidator().validate("non-numeric"))
                .isExactlyInstanceOf(IllegalPageException.class);
    }

    @Test
    public void when_validating_negative_page__exception_is_thrown() {
        assertThatThrownBy(() -> new PageValidator().validate("-2"))
                .isExactlyInstanceOf(IllegalPageException.class);
    }

    @Test
    public void when_validating_null_pointer_page__exception_is_thrown() {
        assertThatThrownBy(() -> new PageValidator().validate(null))
                .isExactlyInstanceOf(IllegalPageException.class);
    }

    @Test
    public void when_validating_empty_page__exception_is_thrown() {
        assertThatThrownBy(() -> new PageValidator().validate(""))
                .isExactlyInstanceOf(IllegalPageException.class);
    }

}
