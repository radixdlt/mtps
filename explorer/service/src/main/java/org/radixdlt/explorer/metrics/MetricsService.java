package org.radixdlt.explorer.metrics;

import io.reactivex.Observable;
import org.radixdlt.explorer.metrics.model.Metrics;
import org.radixdlt.explorer.system.SystemInfoService;
import org.radixdlt.explorer.system.TestStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.registry.Registry;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

import java.nio.file.Path;

public class MetricsService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final MetricsProvider metricsProvider;

    public MetricsService(long shardCount, Path dumpFile) {
        metricsProvider = new MetricsProvider(shardCount, dumpFile);
    }

    @Override
    public void onStart(StartEvent event) {
        Registry registry = event.getRegistry();
        SystemInfoService systemInfoService = registry.get(SystemInfoService.class);
        TestStateService testStateService = registry.get(TestStateService.class);
        metricsProvider.start(
                systemInfoService.getSystemInfoObserver(),
                testStateService.getTestStateObserver());
        LOGGER.info("Metrics service started successfully");
    }

    @Override
    public void onStop(StopEvent event) {
        metricsProvider.stop();
        LOGGER.info("Metrics service stopped successfully");
    }

    public Metrics getMetrics() {
        return metricsProvider.getMetrics();
    }

    public Observable<Metrics> getMetricsObserver() {
        return metricsProvider.getMetricsObserver();
    }

}
