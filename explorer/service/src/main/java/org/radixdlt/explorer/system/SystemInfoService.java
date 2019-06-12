package org.radixdlt.explorer.system;

import io.reactivex.Observable;
import org.radixdlt.explorer.helper.DataHelper;
import org.radixdlt.explorer.nodes.NodeService;
import org.radixdlt.explorer.system.model.SystemInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

import java.util.Map;

public class SystemInfoService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final SystemInfoProvider infoProvider;

    public SystemInfoService(DataHelper dataHelper, long refreshInterval) {
        infoProvider = new SystemInfoProvider(dataHelper, refreshInterval);
    }

    @Override
    public void onStart(StartEvent event) {
        NodeService nodeService = event.getRegistry().get(NodeService.class);
        infoProvider.start(nodeService.getNodeObserver());
        LOGGER.info("System info service started successfully");
    }

    @Override
    public void onStop(StopEvent event) {
        infoProvider.stop();
        LOGGER.info("System info service stopped successfully");
    }

    public Map<String, SystemInfo> getSystemInfo() {
        return infoProvider.getSystemInfo();
    }

    public Observable<Map<String, SystemInfo>> getSystemInfoObserver() {
        return infoProvider.getSystemInfoObserver();
    }

}
