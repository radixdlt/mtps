package org.radixdlt.explorer.nodes;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import org.radixdlt.explorer.helper.DataHelper;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Monitors the nodes in the targeted network and selects a subset of
 * them that don't serve overlapping shards.
 */
class NodeProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");

    private final PublishSubject<Collection<NodeInfo>> subject;
    private final DataHelper dataHelper;
    private final String nodeFinderUrl;
    private final float fraction;
    private final int maxNodes;
    private final long interval;

    private boolean isStarted;

    /**
     * Creates a new instance of this class, allowing the caller to inject
     * any external dependencies and configurations.
     *
     * @param dataHelper The HTTP client to use internally.
     * @param nodesUrl   The URL to the available nodes resource.
     * @param interval   Milliseconds to wait between refresh calls.
     * @param fraction   The fraction of the available nodes to select.
     * @param max        The max number of nodes to select.
     */
    NodeProvider(DataHelper dataHelper, String nodesUrl, long interval, float fraction, int max) {
        this.subject = PublishSubject.create();
        this.dataHelper = dataHelper;
        this.interval = interval;
        this.fraction = fraction;
        this.maxNodes = max;
        this.nodeFinderUrl = nodesUrl;
        this.isStarted = false;
    }

    /**
     * Returns an observable that asynchronously will provide updates as
     * the known Radix network node space changes. This observable will
     * be null until {@link #start()} or after {@link #stop()} has been
     * called.
     *
     * @return The observable or null.
     */
    Observable<Collection<NodeInfo>> getNodesObservable() {
        return subject;
    }

    /**
     * Starts monitoring nodes in the targeted network and selects a
     * subset of them so that the subset of nodes doesn't serve over
     * lapping shards.
     */
    synchronized void start() {
        if (!isStarted) {
            isStarted = true;
            Observable
                    .interval(1000, interval, MILLISECONDS)
                    .takeWhile(number -> isStarted)
                    .subscribe(number -> selectNodes());
        }
    }

    /**
     * Stops the monitoring of nodes and completes any running support
     * services (the scheduler and the observable).
     */
    synchronized void stop() {
        if (isStarted) {
            isStarted = false;
            subject.onComplete();
        }
    }

    /**
     * Requests all nodes from the node finder and selects a subset of
     * them that do not serve overlapping shards.
     */
    private void selectNodes() {
        // The node-finder holds on to its cache much longer than we do
        // and it will return a list of nodes quite long after the network
        // has actually actually been brought down.
        LOGGER.info("Requesting nodes info");
        dataHelper.getData(NodeInfo[].class, nodeFinderUrl, null)
                .onErrorReturnItem(new NodeInfo[0])
                .subscribe(info -> {
                    List<NodeInfo> nodes = Arrays.asList(info);
                    LOGGER.info("Received nodes: {}", nodes.size());
                    nodes.sort(null);
                    selectNonOverlappingSubset(nodes, fraction);
                    subject.onNext(nodes);
                });
    }

    /**
     * Marks a subset of nodes that don't serve overlapping shards as
     * "selected" while also honoring a maximum subset size.
     *
     * @param nodes    The full list of decorated node information.
     * @param fraction The fraction of the full list of nodes to select.
     */
    private void selectNonOverlappingSubset(List<NodeInfo> nodes, float fraction) {
        int desiredSubsetSize = Math.round(nodes.size() * fraction);
        int subsetThreshold = Math.max(1, Math.min(maxNodes, desiredSubsetSize));
        int selectedCount = 0;

        NodeInfo prime = null;
        for (NodeInfo node : nodes) {
            if (prime == null || !prime.isOverlappedBy(node)) {
                node.setSelected(true);
                selectedCount++;
                prime = node;
            } else {
                node.setSelected(false);
            }

            if (selectedCount == subsetThreshold) {
                break;
            }
        }
    }

}
