package org.radixdlt.explorer.nodes;

import io.reactivex.Observable;
import org.radixdlt.explorer.helper.DataHelper;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

import java.util.Collection;

public class NodeService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final NodeProvider nodeProvider;

    public NodeService(DataHelper dataHelper, String nodeFinderUrl, long refreshInterval, float subsetFraction, int maxCount) {
        nodeProvider = new NodeProvider(dataHelper, nodeFinderUrl, refreshInterval, subsetFraction, maxCount);
    }

    @Override
    public void onStart(StartEvent event) {
        nodeProvider.start();
        LOGGER.info("Node service started successfully");
    }

    @Override
    public void onStop(StopEvent event) {
        nodeProvider.stop();
        LOGGER.info("Node service stopped successfully");
    }

    public Observable<Collection<NodeInfo>> getNodeObserver() {
        return nodeProvider.getNodesObservable();
    }

}
