package org.radixdlt.explorer.system;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.radixdlt.explorer.system.model.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.radixdlt.explorer.system.TestState.UNKNOWN;

/**
 * Enables means of getting information on the current test state.
 */
class TestStateProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");

    private final PublishSubject<TestState> subject;
    private final CompositeDisposable disposables;
    private final Map<String, SystemInfo> systemInfo;
    private final Collection<NodeInfo> nodeInfo;
    private final Object systemInfoLock;
    private final Object nodeInfoLock;
    private final Path stateDumpPath;
    private final int measuringThreshold;
    private final float maxNodeDeclineFraction;


    private TestState currentState;
    private boolean isStarted;
    private int highNodeCount;
    private int lowNodeCount;

    /**
     * Creates a new instance of this provider.
     *
     * @param stateDumpPath         Optional path to the file where the
     *                              last calculated test run state is
     *                              persisted.
     * @param nodeDeclineThreshold  The fraction of dropped nodes required
     *                              to finish.
     * @param measuringTpsThreshold The minimum TPS value that must be reached
     *                              in order to consider the test RUNNING.
     */
    TestStateProvider(int measuringTpsThreshold, float nodeDeclineThreshold, Path stateDumpPath) {
        this.subject = PublishSubject.create();
        this.disposables = new CompositeDisposable();
        this.stateDumpPath = stateDumpPath;
        this.systemInfoLock = new Object();
        this.nodeInfoLock = new Object();
        this.systemInfo = new ConcurrentHashMap<>();
        this.nodeInfo = ConcurrentHashMap.newKeySet();
        this.currentState = UNKNOWN;
        this.isStarted = false;
        this.measuringThreshold = measuringTpsThreshold;
        this.maxNodeDeclineFraction = nodeDeclineThreshold;
        this.highNodeCount = Integer.MIN_VALUE;
        this.lowNodeCount = Integer.MAX_VALUE;
    }

    /**
     * Returns the last validated test state.
     *
     * @return The state.
     */
    TestState getState() {
        return currentState;
    }

    /**
     * Returns an observable that asynchronously will provide updated
     * test state info. This observable will be null until {@link
     * #start(Observable, Observable)} or after {@link #stop()} has been
     * called.
     *
     * @return The observable or null.
     */
    Observable<TestState> getStateObserver() {
        return subject;
    }

    /**
     * Starts the validation of test state.
     *
     * @param nodesObserver      The callback that provides information on
     *                           node changes.
     * @param systemInfoObserver The callback that provides information on
     *                           system info changes.
     */
    synchronized void start(Observable<Collection<NodeInfo>> nodesObserver, Observable<Map<String, SystemInfo>> systemInfoObserver) {
        if (!isStarted) {
            isStarted = true;
            if (stateDumpPath != null) {
                // Ensure the full directory structure to the dump file
                // exists before we attempt to write to it.
                stateDumpPath.toAbsolutePath().getParent().toFile().mkdirs();
            }
            disposables.add(systemInfoObserver.subscribe(this::updateSystemInfo));
            disposables.add(nodesObserver.subscribe(this::updateNodeInfo));
            restoreTestState();
        }
    }

    /**
     * Stops the validation of test state.
     */
    synchronized void stop() {
        if (isStarted) {
            isStarted = false;
            disposables.clear();
            subject.onComplete();
        }
    }

    /**
     * Updates the internal cache of available nodes to request system
     * info from.
     *
     * @param newNodeInfo The new collection of nodes.
     */
    private void updateNodeInfo(Collection<NodeInfo> newNodeInfo) {
        synchronized (nodeInfoLock) {
            int size = newNodeInfo.size();
            if (size > highNodeCount) {
                highNodeCount = size;
            }

            if (size < lowNodeCount) {
                lowNodeCount = size;
            }

            nodeInfo.clear();

            if (size <= 1) {
                // We've hit rock bottom, reset our
                // extreme values and bail out.
                highNodeCount = Integer.MIN_VALUE;
                lowNodeCount = Integer.MAX_VALUE;
                return;
            }

            // Check we have enough nodes to continue
            // running the test.
            float s = (float) size;
            float threshold = highNodeCount * (1f - maxNodeDeclineFraction);
            if (s > threshold) {
                nodeInfo.addAll(newNodeInfo);
            }
        }

        validateTestState();
    }

    /**
     * Updates the internal cache of available nodes system info.
     *
     * @param newSystemInfo The new collection of nodes.
     */
    private void updateSystemInfo(Map<String, SystemInfo> newSystemInfo) {
        synchronized (systemInfoLock) {
            systemInfo.clear();
            systemInfo.putAll(newSystemInfo);
        }

        validateTestState();
    }

    /**
     * Validates the nodes system info and deducts the state of the
     * current test from that.
     */
    private void validateTestState() {
        TestState testState = currentState.validate(hasNodeInfo(), isMeasuring());
        if (testState != currentState) {
            currentState = testState;
            dumpCurrentTestState();
            subject.onNext(currentState);
        }
    }

    /**
     * Checks whether the local node info collection is empty or not.
     *
     * @return Boolean true if there is data in the local node info
     * collection, otherwise false.
     */
    private boolean hasNodeInfo() {
        synchronized (nodeInfoLock) {
            // The node-finder will always return the boot node in order
            // to guarantee an existing entry point in to the network for
            // any clients. But we don't want to consider the boot node as
            // a valid metrics providing node.
            return nodeInfo.size() > 1;
        }
    }

    /**
     * Checks whether test is currently running or not.
     *
     * @return Boolean true if test is running, false otherwise.
     */
    private boolean isMeasuring() {
        // We can't be measuring if there is no network to measure on.
        if (!hasNodeInfo()) {
            return false;
        }

        // The storingPerShard works well for detecting when test is about to start
        // but sucks for detecting when test is finished.
        //
        // TODO: Piers wants to transition to FINISHED state when >=90% of the data has been
        // validated (storedPerShard). Problem is that systemInfo is not normalised like in
        // MetricsProvide.calculateMetrics.      
        if (currentState == TestState.STARTED) {
            return (currentState.getStartTimestamp() + 60 * 60 * 1000) > System.currentTimeMillis();
        }

        // sum(radixdlt_core_ledger{key="storing_per_shard"})
        // When the cumulative TPS is above the threshold then the test is
        // considered running
        double averageStoringPerShard = 0;
        synchronized (systemInfoLock) {
            double numNodes = systemInfo.size();
            for (SystemInfo info : systemInfo.values()) {
                averageStoringPerShard += (info.getStoringPerShard() / numNodes);
                if (averageStoringPerShard >= measuringThreshold) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Dumps the current test state to a file if a path to it has been set.
     */
    private void dumpCurrentTestState() {
        if (stateDumpPath != null) {
            try {
                byte[] data = currentState.toString().getBytes(UTF_8);
                Files.write(stateDumpPath, data, CREATE, WRITE);
            } catch (Exception e) {
                LOGGER.info("Couldn't persist current state: " + currentState, e);
            }
        }
    }

    /**
     * Restores the last test state from a file if a path to it has been set.
     */
    private void restoreTestState() {
        if (stateDumpPath != null) {
            try {
                List<String> lines = Files.readAllLines(stateDumpPath, UTF_8);
                String lastLine = lines.get(lines.size() - 1);
                currentState = TestState.fromCSV(lastLine);
            } catch (Exception e) {
                LOGGER.info("Couldn't restore test state, falling back to default", e);
                currentState = UNKNOWN;
                dumpCurrentTestState();
            }
        }
    }

}
