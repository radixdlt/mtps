package org.radixdlt.explorer.system;

import io.reactivex.Emitter;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.radixdlt.explorer.system.model.SystemInfo;
import org.radixdlt.explorer.config.Configuration;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enables means of getting information on the current test state.
 */
class TestStateProvider {
    private final Map<String, SystemInfo> systemInfo;
    private final Collection<NodeInfo> nodeInfo;
    private final Object systemInfoLock;
    private final Object nodeInfoLock;

    private CompositeDisposable disposables;
    private Observable<TestState> observable;
    private Emitter<TestState> emitter;

    private TestState currentState;
    private boolean isStarted;

    /**
     * Creates a new instance of this provider.
     */
    TestStateProvider() {
        this.disposables = new CompositeDisposable();
        systemInfoLock = new Object();
        nodeInfoLock = new Object();
        systemInfo = new ConcurrentHashMap<>();
        nodeInfo = ConcurrentHashMap.newKeySet();
        currentState = TestState.UNKNOWN;
        isStarted = false;
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
        return observable;
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
            disposables.add(systemInfoObserver.subscribe(this::updateSystemInfo));
            disposables.add(nodesObserver.subscribe(this::updateNodeInfo));
            observable = Observable.create(source -> emitter = source);
        }
    }

    /**
     * Stops the validation of test state.
     */
    synchronized void stop() {
        if (isStarted) {
            isStarted = false;
            disposables.clear();
            emitter = null;
            observable = null;
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
            nodeInfo.clear();
            nodeInfo.addAll(newNodeInfo);
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
            if (emitter != null) {
                emitter.onNext(currentState);
            }
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
            return !nodeInfo.isEmpty();
        }
    }

    /**
     * Checks whether test is currently running or not.
     *
     * @return Boolean true if test is running, false otherwise.
     */
    private boolean isMeasuring() {
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
        // When the cumulative TPS is above 10kTPS then the test is considered running
        int threshold = Configuration.getInstance().getTestRunningThreshlod();
        double averageStoringPerShard = 0;
        synchronized (systemInfoLock) {
            double numNodes = systemInfo.size();
            for (SystemInfo info : systemInfo.values()) {
                averageStoringPerShard += (info.getStoringPerShard() / numNodes);
                if (averageStoringPerShard >= threshold) {
                    return true;
                }
            }
            return false;
        }
    }
}
