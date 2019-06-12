package org.radixdlt.explorer.nodes.model;

import java.util.Comparator;
import java.util.Objects;

/**
 * Represents a known node, by address, along with the corresponding system info.
 */
public class NodeInfo implements Comparable<NodeInfo> {
    private static final int SHARD_CHUNKS = 1 << 20;
    private static final long SHARD_CHUNK_RANGE = -(Long.MIN_VALUE / SHARD_CHUNKS) * 2;
    private static final long SHARD_CHUNK_HALF_RANGE = -(Long.MIN_VALUE / SHARD_CHUNKS);

    private final String address;
    private final long anchor;
    private final long high;
    private final long low;

    private transient boolean isSelected;

    /**
     * Creates a new instance of this class.
     *
     * @param address The address of the represented node.
     * @param anchor  The shard anchor point.
     * @param high    The upper bound of the served shard space.
     * @param low     The lower bound of the served shard space.
     */
    public NodeInfo(String address, long anchor, long high, long low) {
        this(address, anchor, high, low, false);
    }

    /**
     * Creates a new instance of this class.
     *
     * @param address  The address of the represented node.
     * @param anchor   The shard anchor point.
     * @param high     The upper bound of the served shard space.
     * @param low      The lower bound of the served shard space.
     * @param selected The selected state.
     */
    public NodeInfo(String address, long anchor, long high, long low, boolean selected) {
        this.address = address;
        this.isSelected = selected;
        this.anchor = anchor;
        this.high = high;
        this.low = low;
    }

    /**
     * @return The node address.
     */
    public String getAddress() {
        return address;
    }

    /**
     * @return The shard anchor point.
     */
    public long getAnchor() {
        return anchor;
    }

    /**
     * @return The lower bound of the served shard space.
     */
    public long getLow() {
        return low;
    }

    /**
     * @return The upper bound of the served shard space.
     */
    public long getHigh() {
        return high;
    }

    /**
     * Checks whether another node serves a shard space which partially
     * or fully overlaps the shard space this node is serving.
     *
     * @param other The other node.
     * @return Boolean true if there is an overlap, or false.
     */
    public boolean isOverlappedBy(NodeInfo other) {
        if (low == 0 && high == 0) {
            return false;
        }

        return other.high >= low && other.low <= high;
    }

    /**
     * Checks whether a given shard falls within the shard space this
     * node is serving.
     *
     * @param shard The shard to check.
     * @return Boolean true if there is an overlap, or false.
     */
    public boolean isOverlappedBy(long shard) {
        if (low == 0 && high == 0) {
            return false;
        }

        long remainder = (shard % SHARD_CHUNK_HALF_RANGE);
        return remainder >= low && remainder <= high;
    }

    /**
     * @return Whether this object is selected or not.
     */
    public boolean isSelected() {
        return isSelected;
    }

    /**
     * Sets the selected state of this object.
     *
     * @param selected The new selected state.
     */
    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NodeInfo) {
            NodeInfo other = (NodeInfo) obj;
            return Objects.equals(this.address, other.address) &&
                    this.getAnchor() == other.getAnchor() &&
                    this.getHigh() == other.getHigh() &&
                    this.getLow() == other.getLow();
        }
        return false;
    }

    @Override
    public int hashCode() {
        long anchor = getAnchor();
        long high = getHigh();
        long low = getLow();

        int result = 1;
        result = 31 * result + (int) (anchor ^ (anchor >>> 32));
        result = 31 * result + (int) (high ^ (high >>> 32));
        result = 31 * result + (int) (low ^ (low >>> 32));
        result = 31 * result + (address != null ? address.hashCode() : 0);

        return result;
    }

    @Override
    public String toString() {
        return "" + address + "; " + getAnchor() + "; " + getHigh() + "; " + getLow();
    }

    @Override
    public int compareTo(NodeInfo other) {
        return Comparator.comparingLong(NodeInfo::getAnchor).compare(this, other);
    }

}
