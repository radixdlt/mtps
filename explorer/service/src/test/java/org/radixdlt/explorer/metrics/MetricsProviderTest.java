package org.radixdlt.explorer.metrics;

import io.reactivex.Observable;
import io.reactivex.Observer;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.radixdlt.explorer.metrics.model.Metrics;
import org.radixdlt.explorer.nodes.model.NodeInfo;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.radixdlt.explorer.metrics.Utils.getMockClient;

public class MetricsProviderTest {

    @Test
    public void when_processed_1_transaction_over_100_ms__correct_metrics_are_calculated() {
        OkHttpClient mockClient = getMockClient(
                "{\"ledger\":{\"stored\":1}}",
                "{\"data\":[{\"temporalProof\":{\"vertices\":[{\"rclock\":1}]}}]}",
                "{\"ledger\":{\"stored\":2}}");

        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        NodeInfo mockNode = new NodeInfo("192.168.0.1", 0L, 1L, 2L, true);

        MetricsProvider metricsProvider = new MetricsProvider(mockClient, "user", "pwd", 100, 1);
        metricsProvider.start(Observable.just(Collections.singletonList(mockNode)));
        metricsProvider.getMetricsObserver().subscribe(mockObserver);

        ArgumentCaptor<Metrics> captor = ArgumentCaptor.forClass(Metrics.class);
        verify(mockObserver, timeout(400L).times(2)).onNext(captor.capture());

        Metrics metrics = captor.getValue();
        assertThat(metrics.getAge()).isGreaterThan(100L);

        double atoms = (2 - 1);                         // From mock JSON above
        double shardsServed = ((2 - 1) + 1);            // From mock node info above
        double shardsTotal = 1;                         // From MetricsProvider constructor above
        double seconds = metrics.getAge() / 1000.0;     // From common sense

        double expectedTps = (atoms / seconds / shardsServed) * shardsTotal;
        double actualTps = metrics.getTps();

        // Allow some minimal slack with ±0.1
        assertThat(actualTps).isBetween(expectedTps - 0.1, expectedTps + 0.1);
        assertThat(metrics.getProgress()).isEqualTo(2);
    }

    @Test
    public void when_calculating_tps__it_is_extrapolated_to_account_for_the_full_network() {
        OkHttpClient mockClient = getMockClient(
                "{\"ledger\":{\"stored\":1}}",                                      // api/system for node 1
                "{\"ledger\":{\"stored\":1}}",                                      // api/system for node 2
                "{\"data\":[{\"temporalProof\":{\"vertices\":[{\"rclock\":1}]}}]}", // api/atoms?... node1
                "{\"ledger\":{\"stored\":4}}",                                      // api/system for node 1
                "{\"ledger\":{\"stored\":7}}");                                     // api/system for node 2

        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        NodeInfo mockNode1 = new NodeInfo("192.168.0.1", 0L, 1L, 2L, true);
        NodeInfo mockNode2 = new NodeInfo("192.168.0.2", 0L, 3L, 4L, true);
        NodeInfo mockNode3 = new NodeInfo("192.168.0.3", 0L, 5L, 6L, false);

        MetricsProvider metricsProvider = new MetricsProvider(mockClient, "user", "pwd", 100, 6);
        metricsProvider.start(Observable.just(Arrays.asList(mockNode1, mockNode2, mockNode3)));
        metricsProvider.getMetricsObserver().subscribe(mockObserver);

        ArgumentCaptor<Metrics> captor = ArgumentCaptor.forClass(Metrics.class);
        verify(mockObserver, timeout(400L).times(2)).onNext(captor.capture());

        Metrics metrics = captor.getValue();
        assertThat(metrics.getAge()).isGreaterThan(100L);

        double atoms = (7 - 1) + (4 - 1);                       // From mock JSON above
        double shardsServed = ((2 - 1) + 1) + ((4 - 3) + 1);    // From mock node info above
        double shardsTotal = 6;                                 // From MetricsProvider constructor above
        double seconds = metrics.getAge() / 1000.0;             // From common sense

        double expectedTps = (atoms / seconds / shardsServed) * shardsTotal;
        double actualTps = metrics.getTps();

        // Allow some minimal slack with ±0.1
        assertThat(actualTps).isBetween(expectedTps - 0.1, expectedTps + 0.1);
        assertThat(metrics.getProgress()).isEqualTo(7);
    }

    @Test
    public void when_no_nodes_present__no_metrics_are_calculated() {
        OkHttpClient mockClient = getMockClient(
                "{\"ledger\":{\"stored\":1,\"processed\":2}}",
                "{\"data\":[{\"temporalProof\":{\"vertices\":[{\"rclock\":1,\"clock\":2}]}}]}",
                "{\"ledger\":{\"stored\":2,\"processed\":3}}");

        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        MetricsProvider metricsProvider = new MetricsProvider(mockClient, "user", "pwd", 100, 1);
        metricsProvider.start(Observable.empty());
        metricsProvider.getMetricsObserver().subscribe(mockObserver);
        verify(mockObserver, timeout(400L).times(0)).onNext(any());
    }

    @Test
    public void when_requesting_metrics_synchronously__same_data_is_returned_as_through_observer() {
        OkHttpClient mockClient = getMockClient(
                "{\"ledger\":{\"stored\":1,\"processed\":2}}",
                "{\"data\":[{\"temporalProof\":{\"vertices\":[{\"rclock\":1,\"clock\":2}]}}]}",
                "{\"ledger\":{\"stored\":12,\"processed\":14}}");

        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        NodeInfo mockNode = mock(NodeInfo.class);
        when(mockNode.getAddress()).thenReturn("192.168.0.1");

        MetricsProvider metricsProvider = new MetricsProvider(mockClient, "user", "pwd", 100, 1);
        metricsProvider.start(Observable.just(Collections.singletonList(mockNode)));
        metricsProvider.getMetricsObserver().subscribe(mockObserver);

        ArgumentCaptor<Metrics> captor = ArgumentCaptor.forClass(Metrics.class);
        verify(mockObserver, timeout(400L).times(2)).onNext(captor.capture());

        Metrics metrics1 = captor.getValue();
        Metrics metrics2 = metricsProvider.getMetrics();
        assertThat(metrics1.getAge()).isEqualTo(metrics2.getAge());
        assertThat(metrics1.getTps()).isEqualTo(metrics2.getTps());
        assertThat(metrics1.getProgress()).isEqualTo(metrics2.getProgress());
    }

}
