package org.radixdlt.explorer.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.radixdlt.explorer.config.Configuration.DEFAULT_METRICS_INTERVAL;
import static org.radixdlt.explorer.config.Configuration.DEFAULT_METRICS_TOTAL;
import static org.radixdlt.explorer.config.Configuration.DEFAULT_NODES_FRACTION;
import static org.radixdlt.explorer.config.Configuration.DEFAULT_NODES_INTERVAL;
import static org.radixdlt.explorer.config.Configuration.DEFAULT_TRANSACTIONS_PAGE_SIZE;
import static org.radixdlt.explorer.config.Configuration.DEFAULT_UNIVERSE_INTERVAL;
import static org.radixdlt.explorer.config.Configuration.DEFAULT_UNIVERSE_SHARD_COUNT;

public class ConfigurationTest {
    private static final Path CONFIG = Paths.get("config.properties");

    @Before
    public void beforeTest() throws IOException {
        if (Files.exists(CONFIG)) {
            Files.delete(Paths.get("config.properties"));
        }
    }

    @After
    public void afterTest() throws IOException {
        if (Files.exists(CONFIG)) {
            Files.delete(Paths.get("config.properties"));
        }
    }

    // BEGIN: Happy path
    @Test
    public void when_requesting_valid_nodes_url__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "nodes.url=path".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesUrl()).isEqualTo("path");
    }

    @Test
    public void when_requesting_valid_nodes_refresh_interval__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "nodes.interval=10000".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesRefreshInterval()).isEqualTo(10000L);
    }

    @Test
    public void when_requesting_valid_subset_fraction__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "nodes.fraction=0.2".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesSubsetFraction()).isEqualTo(0.2f);
    }

    @Test
    public void when_requesting_existing_username__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "nodes.username=dan".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesAuthUsername()).isEqualTo("dan");
    }

    @Test
    public void when_requesting_existing_password__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "nodes.password=D1tg0d".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesAuthPassword()).isEqualTo("D1tg0d");
    }

    @Test
    public void when_requesting_valid_calculation_interval__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "metrics.interval=1000".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getMetricsCalculationInterval()).isEqualTo(1000L);
    }

    @Test
    public void when_requesting_valid_total_transaction_count__correct_value_is_returned() throws IOException {
        // TODO: Figure out how to test environment variable path too
        Files.write(CONFIG, "metrics.max=12".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getMetricsTotalTransactions()).isEqualTo(12L);
    }

    @Test
    public void when_requesting_existing_transactions_page_size__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "transactions.size=100".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getTransactionsPageSize()).isEqualTo(100);
    }

    @Test
    public void when_requesting_valid_shards_count__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "universe.shards=2.0E2".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getUniverseShardCount()).isEqualTo(200.0);
    }

    @Test
    public void when_requesting_existing_universe_retry_interval__correct_value_is_returned() throws IOException {
        Files.write(CONFIG, "universe.interval=22".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getUniverseRetryInterval()).isEqualTo(22);
    }
    // END: Happy path

    // BEGIN: Unhappy path
    @Test
    public void when_requesting_invalid_nodes_refresh_interval__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "nodes.interval=invalid".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesRefreshInterval()).isEqualTo(DEFAULT_NODES_INTERVAL);
    }

    @Test
    public void when_requesting_invalid_subset_fraction__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "nodes.fraction=invalid".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesSubsetFraction()).isEqualTo(DEFAULT_NODES_FRACTION);
    }

    @Test
    public void when_requesting_invalid_calculation_interval__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "metrics.interval=invalid".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getMetricsCalculationInterval()).isEqualTo(DEFAULT_METRICS_INTERVAL);
    }

    @Test
    public void when_requesting_invalid_total_transactions_count__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "metrics.max=invalid".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getMetricsTotalTransactions()).isEqualTo(DEFAULT_METRICS_TOTAL);
    }

    @Test
    public void when_requesting_invalid_transactions_page_size__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "transactions.size=invalid".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getTransactionsPageSize()).isEqualTo(DEFAULT_TRANSACTIONS_PAGE_SIZE);
    }

    @Test
    public void when_requesting_invalid_shards_count__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "universe.shards=invalid".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getUniverseShardCount()).isEqualTo(DEFAULT_UNIVERSE_SHARD_COUNT);
    }

    @Test
    public void when_requesting_invalid_universe_retry_interval__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "universe.interval=invalid".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getUniverseRetryInterval()).isEqualTo(DEFAULT_UNIVERSE_INTERVAL);
    }

    @Test
    public void when_requesting_non_existing_nodes_url__null_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesUrl()).isNull();
    }

    @Test
    public void when_requesting_non_existing_nodes_refresh_interval__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesRefreshInterval()).isEqualTo(DEFAULT_NODES_INTERVAL);
    }

    @Test
    public void when_requesting_non_existing_subset_fraction__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesSubsetFraction()).isEqualTo(DEFAULT_NODES_FRACTION);
    }

    @Test
    public void when_requesting_non_existing_username__null_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesAuthUsername()).isNull();
    }

    @Test
    public void when_requesting_non_existing_password__null_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getNodesAuthPassword()).isNull();
    }

    @Test
    public void when_requesting_non_existing_calculation_interval__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getMetricsCalculationInterval()).isEqualTo(DEFAULT_METRICS_INTERVAL);
    }

    @Test
    public void when_requesting_non_existing_total_transactions_count__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getMetricsTotalTransactions()).isEqualTo(DEFAULT_METRICS_TOTAL);
    }

    @Test
    public void when_requesting_non_existing_transactions_page_size__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getTransactionsPageSize()).isEqualTo(DEFAULT_TRANSACTIONS_PAGE_SIZE);
    }

    @Test
    public void when_requesting_non_existing_shards_count__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getUniverseShardCount()).isEqualTo(DEFAULT_UNIVERSE_SHARD_COUNT);
    }

    @Test
    public void when_requesting_non_existing_universe_retry_interval__default_value_is_returned() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThat(Configuration.getInstance().getUniverseRetryInterval()).isEqualTo(DEFAULT_UNIVERSE_INTERVAL);
    }
    // END: Unhappy path

    // BEGIN: Extreme
    @Test
    public void when_properties_file_does_not_exist__no_exception_is_thrown() {
        assertThatCode(() -> Configuration.getInstance().reload()).doesNotThrowAnyException();
    }

    @Test
    public void when_requesting_configuration_from_empty_file__no_exception_is_thrown() throws IOException {
        Files.write(CONFIG, "".getBytes());
        Configuration.getInstance().reload();
        assertThatCode(() -> Configuration.getInstance().getNodesUrl()).doesNotThrowAnyException();
        assertThatCode(() -> Configuration.getInstance().getNodesSubsetFraction()).doesNotThrowAnyException();
        assertThatCode(() -> Configuration.getInstance().getNodesRefreshInterval()).doesNotThrowAnyException();
        assertThatCode(() -> Configuration.getInstance().getNodesAuthUsername()).doesNotThrowAnyException();
        assertThatCode(() -> Configuration.getInstance().getNodesAuthPassword()).doesNotThrowAnyException();
        assertThatCode(() -> Configuration.getInstance().getMetricsCalculationInterval()).doesNotThrowAnyException();
    }
    // END: Extreme

}
