package org.radixdlt.explorer;

import org.junit.Test;
import org.radixdlt.explorer.metrics.model.Metrics;
import org.radixdlt.explorer.transactions.model.TransactionInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class WrapperTest {

    @Test
    public void when_wrapping_a_metrics_object__valid_type_is_set() {
        Metrics metrics = new Metrics(0, 0f, 0L, 0L);
        Wrapper wrapper = Wrapper.of(metrics);
        assertThat(wrapper.getType()).isEqualTo("metrics");
        assertThat(wrapper.getData()).isEqualTo(metrics);
    }

    @Test
    public void when_wrapping_a_transactions_array__valid_type_is_set() {
        TransactionInfo[] transactions = new TransactionInfo[0];
        Wrapper wrapper = Wrapper.of(transactions);
        assertThat(wrapper.getType()).isEqualTo("transactions");
        assertThat(wrapper.getData()).isEqualTo(transactions);
    }

    @Test
    public void when_no_meta_data_is_added__then_a_null_pointer_map_is_returned() {
        TransactionInfo[] transactions = new TransactionInfo[0];
        Wrapper wrapper = Wrapper.of(transactions);
        assertThat(wrapper.getMeta()).isNull();
    }

    @Test
    public void when_only_null_pointer_meta_data_is_added__then_a_null_pointer_map_is_returned() {
        TransactionInfo[] transactions = new TransactionInfo[0];
        Wrapper wrapper = Wrapper.of(transactions)
                .addMetaData(null, "a")
                .addMetaData("b", null);
        assertThat(wrapper.getMeta()).isNull();
    }

    @Test
    public void when_additional_null_pointer_meta_data_is_added__it_is_ignored() {
        TransactionInfo[] transactions = new TransactionInfo[0];
        Wrapper wrapper = Wrapper.of(transactions)
                .addMetaData("c", "d")
                .addMetaData("b", null);
        assertThat(wrapper.getMeta().get("b")).isNull();
    }

    @Test
    public void when_meta_data_is_added__it_can_successfully_be_retrieved() {
        TransactionInfo[] transactions = new TransactionInfo[0];
        Wrapper wrapper = Wrapper.of(transactions).addMetaData("a", "b");
        assertThat(wrapper.getMeta().get("a")).isEqualTo("b");
    }

    @Test
    public void when_wrapping_single_transaction__exception_is_thrown() {
        TransactionInfo transaction = new TransactionInfo(null, null, null, null);
        assertThatThrownBy(() -> Wrapper.of(transaction))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void when_wrapping_raw_object__exception_is_thrown() {
        Object object = new Object();
        assertThatThrownBy(() -> Wrapper.of(object))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void when_wrapping_null_pointer__exception_is_thrown() {
        assertThatThrownBy(() -> Wrapper.of(null))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

}
