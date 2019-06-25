package org.radixdlt.explorer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import static java.util.concurrent.TimeUnit.SECONDS;


/**
 * Exposes the application settings.
 */
public final class Configuration {
    public static final int DEFAULT_TRANSACTIONS_PAGE_SIZE = 50;
    public static final long DEFAULT_METRICS_INTERVAL = SECONDS.toMillis(10);
    public static final long DEFAULT_METRICS_TOTAL = 100;
    public static final long DEFAULT_NODES_INTERVAL = SECONDS.toMillis(30);
    public static final float DEFAULT_NODES_FRACTION = 0.1f;
    public static final int DEFAULT_NODES_MAX_COUNT = 20;
    public static final long DEFAULT_UNIVERSE_SHARD_COUNT = 1L;
    public static final int DEFAULT_TEST_RUNNING = 100;
    public static final long DEFAULT_NEXT_TEST = 0L;
    public static final int DEFAULT_UNIVERSE_MAGIC = -849412095;
    public static final String CONFIG_FILE = "/opt/radixdlt/service/data/config.properties";
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");

    private final Properties properties;

    /**
     * Intentionally hidden and aggressively throwing constructor.
     */
    private Configuration() {
        properties = new Properties();
        reload();
    }

    /**
     * @return The singleton instance of this class.
     */
    public static Configuration getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Reloads the settings from the properties file. This method should
     * not be called by application logic. It's intended for testing
     * purposes only.
     */
    synchronized void reload() {
        try (InputStream source = new FileInputStream(CONFIG_FILE)) {
            properties.clear();
            properties.load(source);
        } catch (IOException e) {
            LOGGER.error("Couldn't read configuration properties", e);
        }
    }

    /**
     * @return The path segment for the node finder.
     */
    public synchronized String getNodesUrl() {
        return properties.getProperty("nodes.url");
    }

    /**
     * @return The number of milliseconds to wait between node refresh calls.
     */
    public synchronized long getNodesRefreshInterval() {
        String value = properties.getProperty("nodes.interval");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Couldn't read nodes refresh interval property, falling back to default", e);
            return DEFAULT_NODES_INTERVAL;
        }
    }

    /**
     * @return The fraction of the total amount of available nodes in
     * the network to select for metrics calculation.
     */
    public synchronized float getNodesSubsetFraction() {
        String value = properties.getProperty("nodes.fraction");
        try {
            return Float.parseFloat(value);
        } catch (NullPointerException | NumberFormatException e) {
            LOGGER.warn("Couldn't read nodes subset fraction, falling back to default", e);
            return DEFAULT_NODES_FRACTION;
        }
    }

    /**
     * @return The max number of nodes to select for metrics calculation.
     */
    public synchronized int getMaxNodesSubsetCount() {
        String value = properties.getProperty("nodes.max");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Couldn't read max nodes subset count, falling back to default", e);
            return DEFAULT_NODES_MAX_COUNT;
        }
    }

    /**
     * @return The username to use when authenticating some requests
     * to a core node.
     */
    public synchronized String getNodesAuthUsername() {
        return properties.getProperty("nodes.username");
    }

    /**
     * @return The password to use when authenticating some requests
     * to a core node.
     */
    public synchronized String getNodesAuthPassword() {
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null) {
            password = properties.getProperty("nodes.password");
        }

        return password;
    }

    /**
     * @return The number of milliseconds to wait between metrics
     * calculations.
     */
    public synchronized long getMetricsCalculationInterval() {
        String value = properties.getProperty("metrics.interval");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Couldn't read metrics calculation interval property, falling back to default", e);
            return DEFAULT_METRICS_INTERVAL;
        }
    }

    /**
     * @return The total number of transactions to expect.
     */
    public synchronized long getMetricsTotalTransactions() {
        String value = System.getenv("TRANSACTIONS_TOTAL");
        if (value == null) {
            value = properties.getProperty("metrics.max");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Couldn't read total transactions count property, falling back to default", e);
            return DEFAULT_METRICS_TOTAL;
        }
    }

    /**
     * @return The path to the file where any calculated metrics should be dumped.
     */
    public synchronized Path getMetricsDumpFilePath() {
        String value = properties.getProperty("metrics.dump");
        return value != null ? Paths.get(value) : null;
    }

    /**
     * @return The static number of transactions per page.
     */
    public synchronized int getTransactionsPageSize() {
        String value = properties.getProperty("transactions.size");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Couldn't read transactions page size property, falling back to default", e);
            return DEFAULT_TRANSACTIONS_PAGE_SIZE;
        }
    }

    /**
     * @return The maximum number of shards in the Radix network.
     */
    public synchronized long getUniverseShardCount() {
        String value = properties.getProperty("universe.shards");
        try {
            // BigDecimal knows how to parse scientific numbers (e.g. 18E+44).
            return new BigDecimal(value).longValueExact();
        } catch (NullPointerException | NumberFormatException e) {
            LOGGER.warn("Couldn't read universe shard count property, falling back to default", e);
            return DEFAULT_UNIVERSE_SHARD_COUNT;
        }
    }

    /**
     * @return The universe magic number, which the test data was encoded with.
     */
    public synchronized int getUniverseMagic() {
        String value = properties.getProperty("universe.magic");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Couldn't read universe magic property, falling back to default", e);
            return DEFAULT_UNIVERSE_MAGIC;
        }
    }

    /**
     * @return The path to the file where the test state should be dumped.
     */
    public synchronized Path getTestStateDumpFilePath() {
        String value = properties.getProperty("test.dump");
        return value != null ? Paths.get(value) : null;
    }

    /**
     * @return The minimum TPS count that must be reached in order to
     * consider the test running.
     */
    public synchronized int getTestRunningThreshold() {
        String value = properties.getProperty("test.running");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Couldn't read test retry running threshold, falling back to default", e);
            return DEFAULT_TEST_RUNNING;
        }
    }

    /**
     * @return The timestamp for the next test run.
     */
    public synchronized long getNextTestRunUtc() {
        String value = properties.getProperty("test.next");
        return getNextTestDateTimestamp(value);
    }

    /**
     * Selects the next timestamp from the next test timestamps.
     *
     * @param testDatesFile The name of the file containing the future
     *                      test timestamps.
     * @return The next test timestamp.
     */
    private long getNextTestDateTimestamp(String testDatesFile) {
        if (testDatesFile == null) {
            LOGGER.info("Timestamps file mustn't be null pointer, falling back to default");
            return DEFAULT_NEXT_TEST;
        }

        Path path = Paths.get(testDatesFile);
        if (!Files.exists(path)) {
            LOGGER.info("Couldn't read timestamps from file, falling back to default: " +
                    path.toAbsolutePath().toString());
            return DEFAULT_NEXT_TEST;
        }

        try {
            // We hope that people won't be able to add more lines to
            // this file than what will fit in runtime memory.
            long now = System.currentTimeMillis();
            return Files.readAllLines(path)
                    .stream()
                    .map(line -> {
                        try {
                            return Long.parseLong(line);
                        } catch (NumberFormatException e) {
                            LOGGER.info("Couldn't parse timestamp, falling back to default: " + line);
                            return DEFAULT_NEXT_TEST;
                        }
                    })
                    .filter(timestamp -> timestamp > now)
                    .findFirst()
                    .orElse(DEFAULT_NEXT_TEST);
        } catch (Exception e) {
            LOGGER.info("Couldn't read timestamps from file, falling back to default", e);
            return DEFAULT_NEXT_TEST;
        }
    }

    /**
     * Lazy loader helper class as of the initialize-on-demand holder
     * idiom. Note that there are risks with this implementation, would
     * the constructor if {@link Configuration} fail for any reason.
     * <p>
     * More info here:
     * https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
     */
    private static final class Holder {
        static final Configuration INSTANCE = new Configuration();
    }

}
