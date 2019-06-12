package org.radixdlt.explorer.system;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;
import org.radixdlt.explorer.helper.DataHelper;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.radixdlt.explorer.system.model.SystemInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class SystemInfoProvider {
    private final PublishSubject<Map<String, SystemInfo>> subject;
    private final CompositeDisposable disposables;
    private final Map<String, SystemInfo> info;
    private final Collection<NodeInfo> nodes;
    private final Object nodesUpdateLock;
    private final DataHelper dataHelper;
    private final long interval;

    private boolean isStarted;


    SystemInfoProvider(DataHelper dataHelper, long interval) {
        this.subject = PublishSubject.create();
        this.disposables = new CompositeDisposable();
        this.nodes = ConcurrentHashMap.newKeySet();
        this.info = new ConcurrentHashMap<>();
        this.nodesUpdateLock = new Object();
        this.dataHelper = dataHelper;
        this.interval = interval;
        this.isStarted = false;
    }


    /**
     * Returns the last collected system info data.
     *
     * @return The unmodifiable map of the collected data.
     */
    Map<String, SystemInfo> getSystemInfo() {
        return Collections.unmodifiableMap(info);
    }

    /**
     * Returns an observable that asynchronously will provide updated
     * system info. This observable will be null until {@link
     * #start(Observable)} or after {@link #stop()} has been called.
     *
     * @return The observable or null.
     */
    Observable<Map<String, SystemInfo>> getSystemInfoObserver() {
        return subject;
    }

    /**
     * Starts the collecting of system metrics on a periodic basis.
     *
     * @param nodesObserver The callback that provides information on
     *                      node changes.
     */
    synchronized void start(Observable<Collection<NodeInfo>> nodesObserver) {
        if (!isStarted) {
            isStarted = true;
            disposables.add(nodesObserver.subscribe(this::updateNodes));
            Observable
                    .interval(1000, interval, MILLISECONDS)
                    .takeWhile(number -> isStarted)
                    .subscribe(number -> collectSystemInfo());
        }
    }

    /**
     * Stops the collecting of system metrics.
     */
    synchronized void stop() {
        if (isStarted) {
            isStarted = false;
            subject.onComplete();
            disposables.clear();
        }
    }

    /**
     * Updates the internal cache of available nodes to request system
     * info from.
     *
     * @param newNodes The new collection of nodes.
     */
    private void updateNodes(Collection<NodeInfo> newNodes) {
        synchronized (nodesUpdateLock) {
            nodes.clear();
            nodes.addAll(newNodes);
        }
    }

    /**
     * Collects system info from a select set of nodes.
     */
    private void collectSystemInfo() {
        List<NodeInfo> selected = nodes.stream()
                .filter(NodeInfo::isSelected)
                .collect(Collectors.toList());

        Map<String, SystemInfo> cache = new ConcurrentHashMap<>();
        AtomicInteger finishCount = new AtomicInteger();
        int size = selected.size();

        selected.forEach(node -> dataHelper
                .getData(SystemInfo.class, node.getAddress(), "api/system")
                .onErrorReturnItem(new SystemInfo())
                .doOnSuccess(info -> {
                    if (!info.isEmpty()) {
                        cache.put(node.getAddress(), info);
                    }
                })
                .doFinally(() -> {
                    if (finishCount.incrementAndGet() == size) {
                        info.clear();
                        info.putAll(cache);
                        subject.onNext(Collections.unmodifiableMap(info));
                    }
                })
                .subscribe());
    }

}
