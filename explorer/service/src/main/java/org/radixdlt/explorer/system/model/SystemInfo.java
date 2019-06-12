package org.radixdlt.explorer.system.model;

/**
 * Holds system information about the node.
 */
public class SystemInfo {
    private final long storing;
    private final double stored;
    private final long anchor;
    private final long high;
    private final long low;
    private final long complex;
    private final long nonComplex;

    public SystemInfo() {
        this(0, 0, 0, 0, 0, 0, 0);
    }

    public SystemInfo(long storing, double stored, long anchor, long high, long low, long complex, long nonComplex) {
        this.storing = storing;
        this.stored = stored;
        this.anchor = anchor;
        this.high = high;
        this.low = low;
        this.complex = complex;
        this.nonComplex = nonComplex;
    }

    public long getStoringPerShard() {
        return storing;
    }

    public double getStoredPerShard() {
        return stored;
    }

    public long getShardAnchor() {
        return anchor;
    }

    public long getShardHighEnd() {
        return high;
    }

    public long getShardLowEnd() {
        return low;
    }

    public long getShardSize() {
        return high - low + 1;
    }

    public long getComplexQueueSize() {
        return complex;
    }

    public long getNonComplexQueueSize() {
        return nonComplex;
    }

    public boolean isEmpty() {
        return storing == 0L && stored == 0L && anchor == 0L && high == 0L && low == 0L && complex == 0L && nonComplex == 0L;
    }

}
