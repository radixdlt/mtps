package org.radixdlt.explorer.metrics.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds information about a snapshot of calculated throughput.
 */
public class Metrics {
    public static final String DATA_HEADLINE = "Timestamp,Spot TPS,Progress,Average TPS,Peak TPS\n";
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");

    private final long spotTps;
    private final long peakTps;
    private final long averageTps;
    private final long progress;

    /**
     * Creates a new metrics DTO instance.
     *
     * @param tps      The transactions-per-second metric.
     * @param progress The current progress of the import [0..1].
     */
    public Metrics(long tps, long progress, long averageTps, long peakTps) {
        this.spotTps = tps;
        this.progress = progress;
        this.averageTps = averageTps;
        this.peakTps = peakTps;
    }

    public static Metrics fromCSV(String line) {
        if (line == null) {
            return null;
        }

        String[] components = line.split(",", 6);
        if (components.length < 5) {
            return null;
        }

        try {
            return new Metrics(
                    Long.valueOf(components[1]),    // spotTps
                    Long.valueOf(components[2]),    // progress
                    Long.valueOf(components[3]),    // averageTps
                    Long.valueOf(components[4]));   // peakTps
        } catch (NumberFormatException e) {
            LOGGER.info("Couldn't parse CSV: " + line, e);
            return null;
        }
    }

    /**
     * @return The calculated transactions-per-seconds metric.
     */
    public long getTps() {
        return spotTps;
    }

    /**
     * @return The progress of the full test as a number between 0 and 1.
     */
    public long getProgress() {
        return progress;
    }

    /**
     * @return The average transactions per second in the network so far.
     */
    public long getAverageTps() {
        return averageTps;
    }

    /**
     * @return The peak transactions per seconds seen in the network so far.
     */
    public long getPeakTps() {
        return peakTps;
    }

    @Override
    public String toString() {
        // NOTE!!! Don't change the order of data without making the
        // corresponding changes to the DATA_HEADLINE constant and the
        // "fromCSV" method.
        return String.format("%d,%d,%d,%d,%d\n", System.currentTimeMillis(), spotTps, progress, averageTps, peakTps);
    }

}
