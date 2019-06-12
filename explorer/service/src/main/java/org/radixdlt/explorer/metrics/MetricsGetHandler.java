package org.radixdlt.explorer.metrics;

import com.google.gson.GsonBuilder;
import org.radixdlt.explorer.Wrapper;
import org.radixdlt.explorer.config.Configuration;
import org.radixdlt.explorer.metrics.model.Metrics;
import org.radixdlt.explorer.system.TestState;
import org.radixdlt.explorer.system.TestStateService;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.Response;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static ratpack.http.Status.OK;

/**
 * Handles any metrics requests.
 */
public class MetricsGetHandler implements Handler {

    @Override
    public void handle(Context context) {
        Configuration configuration = Configuration.getInstance();
        MetricsService metricsService = context.get(MetricsService.class);
        Metrics metrics = metricsService.getMetrics();

        TestStateService testStateService = context.get(TestStateService.class);
        TestState state = testStateService.getTestState();

        if (metrics == null) {
            metrics = new Metrics(0, 0, 0, 0);
        }

        Wrapper wrapper = Wrapper.of(metrics)
                .addMetaData("testState", state.name())
                .addMetaData("testStart", state.getStartTimestamp())
                .addMetaData("testStop", state.getStopTimestamp())
                .addMetaData("progressMax", configuration.getMetricsTotalTransactions());

        long maxAgeConfig = configuration.getMetricsCalculationInterval();
        long maxAge = MILLISECONDS.toSeconds(maxAgeConfig);
        String json = new GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(wrapper);

        Response response = context.getResponse();
        response.getHeaders()
                .set("Content-Type", "application/json")
                .set("Cache-Control", "max-age=" + maxAge);
        response.status(OK)
                .send(json);
    }

}
