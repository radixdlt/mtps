package org.radixdlt.explorer.metrics.model;

/**
 * Holds information about a snapshot of calculated throughput.
 */
public class Metrics {
    public static final String DATA_HEADLINE = "Timestamp,Spot TPS,Progress,Average TPS,Peak TPS\n";
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
        // corresponding changes to the DATA_HEADLINE constant.
        return String.format("%d,%d,%d,%d,%d\n", System.currentTimeMillis(), spotTps, progress, averageTps, peakTps);
    }

}
