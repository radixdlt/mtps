package org.radixdlt.explorer.system;

/**
 * Describes the states this provider can report.
 */
public enum TestState {

    /**
     * Signals that the test has been started and is on-going.
     */
    STARTED,

    /**
     * Signals that the test is finished and the results are still
     * available to verify.
     */
    FINISHED,

    /**
     * Signals that the test is finished and the network has been
     * shut down. The measured metrics are available, but the test
     * data can no longer be reached for verification.
     */
    TERMINATED,

    /**
     * Signals that the test state is unknown. This is a "virtual"
     * state until the first batch of system information has been
     * analysed.
     */
    UNKNOWN;

    private long start = 0L;
    private long stop = 0L;

    TestState validate(boolean hasNodes, boolean isMeasuring) {
        switch (this) {
            case UNKNOWN:
                TestState state;
                if (hasNodes) {
                    if (isMeasuring) {
                        state = STARTED;
                    } else {
                        state = FINISHED;
                    }
                } else {
                    state = TERMINATED;
                }
                state.start = System.currentTimeMillis();
                state.stop = System.currentTimeMillis();
                return state;
            case STARTED:
                if (!isMeasuring) {
                    TestState newState = FINISHED;
                    newState.start = this.start;
                    newState.stop = System.currentTimeMillis();
                    return newState;
                } else {
                    return this;
                }
            case FINISHED:
                if (!hasNodes) {
                    TestState newState = TERMINATED;
                    newState.start = this.start;
                    newState.stop = this.stop;
                    return newState;
                } else {
                    return this;
                }
            case TERMINATED:
                if (hasNodes && isMeasuring) {
                    TestState newState = STARTED;
                    newState.start = System.currentTimeMillis();
                    newState.stop = 0L;
                    return newState;
                } else {
                    return this;
                }
            default:
                // Huh???
                return this;
        }
    }

    public long getStartTimestamp() {
        return start;
    }

    public long getStopTimestamp() {
        return stop;
    }

}
