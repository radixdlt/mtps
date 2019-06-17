package org.radixdlt.explorer.system;

import io.reactivex.Observable;
import org.radixdlt.explorer.nodes.NodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.registry.Registry;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

import java.nio.file.Path;

public class TestStateService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final TestStateProvider testStateProvider;

    public TestStateService(int runningTpsThreshold, Path stateDumpFile) {
        testStateProvider = new TestStateProvider(runningTpsThreshold, stateDumpFile);
    }

    @Override
    public void onStart(StartEvent event) {
        Registry registry = event.getRegistry();
        NodeService nodeService = registry.get(NodeService.class);
        SystemInfoService infoService = registry.get(SystemInfoService.class);
        testStateProvider.start(nodeService.getNodeObserver(), infoService.getSystemInfoObserver());
        LOGGER.info("Test state service started successfully");
    }

    @Override
    public void onStop(StopEvent event) {
        testStateProvider.stop();
        LOGGER.info("Test state service stopped successfully");
    }

    public TestState getTestState() {
        return testStateProvider.getState();
    }

    public Observable<TestState> getTestStateObserver() {
        return testStateProvider.getStateObserver();
    }

}
