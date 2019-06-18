package org.radixdlt.explorer.metrics;

import io.reactivex.Observer;
import io.reactivex.subjects.PublishSubject;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.radixdlt.explorer.metrics.model.Metrics;
import org.radixdlt.explorer.system.TestState;
import org.radixdlt.explorer.system.model.SystemInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.radixdlt.explorer.system.TestState.STARTED;

public class MetricsProviderTest {
    private static final Path TEST_DUMP_FILE_PATH = Paths.get("metrics_provider_test.csv");

    @After
    public void afterTest() throws Exception {
        Files.deleteIfExists(TEST_DUMP_FILE_PATH);
    }


    @Test
    public void when_calculating_tps__it_is_extrapolated_to_account_for_the_full_network() {
        // State machine transport system
        PublishSubject<Map<String, SystemInfo>> systemInfo = PublishSubject.create();
        PublishSubject<TestState> testState = PublishSubject.create();
        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        // This (mock) node is processing 100 atoms per shard,
        // and it serves 10 shards
        SystemInfo mockNode = mock(SystemInfo.class);
        when(mockNode.getStoringPerShard()).thenReturn(100L);
        when(mockNode.getShardSize()).thenReturn(10L);
        when(mockNode.getStoredPerShard()).thenReturn(0.0);

        Map<String, SystemInfo> data = new HashMap<>();
        data.put("0.0.0.1", mockNode);

        // The metrics provider expects the ledger to have a total of 1000 shards
        MetricsProvider metricsProvider = new MetricsProvider(1000, null);
        metricsProvider.start(systemInfo, testState);
        metricsProvider.getMetricsObserver().subscribe(mockObserver);

        // Poke the internal state machine of the metrics provider.
        testState.onNext(STARTED);
        systemInfo.onNext(data);

        ArgumentCaptor<Metrics> captor = ArgumentCaptor.forClass(Metrics.class);
        verify(mockObserver).onNext(captor.capture());

        // The (mock) node processes 1% of the total shard space of the ledger (10 / 1000)
        // at a speed of 100 TPS. Extrapolating that capacity would mean that the network
        // is expected to work at 100 TPS * 100 % => 10 000 TPS.
        Metrics metrics = captor.getValue();
        assertThat(metrics.getTps()).isEqualTo(10000L);
    }

    @Test
    public void when_no_nodes_present__no_metrics_are_calculated() {
        // State machine transport system
        PublishSubject<Map<String, SystemInfo>> systemInfo = PublishSubject.create();
        PublishSubject<TestState> testState = PublishSubject.create();
        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        // Empty (mock) nodes map.
        Map<String, SystemInfo> data = new HashMap<>();

        // The metrics provider expects the ledger to have a total of 1000 shards
        MetricsProvider metricsProvider = new MetricsProvider(1000, null);
        metricsProvider.start(systemInfo, testState);
        metricsProvider.getMetricsObserver().subscribe(mockObserver);

        // Poke the internal state machine of the metrics provider.
        testState.onNext(STARTED);
        systemInfo.onNext(data);

        verify(mockObserver, times(0)).onNext(any());
    }

    @Test
    public void when_requesting_metrics__same_data_is_returned_in_sync_channel_as_in_async_channel() {
        // State machine transport system
        PublishSubject<Map<String, SystemInfo>> systemInfo = PublishSubject.create();
        PublishSubject<TestState> testState = PublishSubject.create();
        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        // This (mock) node is processing 100 atoms per shard,
        // and it serves 10 shards
        SystemInfo mockNode = mock(SystemInfo.class);
        when(mockNode.getStoringPerShard()).thenReturn(100L);
        when(mockNode.getShardSize()).thenReturn(10L);
        when(mockNode.getStoredPerShard()).thenReturn(0.0);

        Map<String, SystemInfo> data = new HashMap<>();
        data.put("0.0.0.1", mockNode);

        // The metrics provider expects the ledger to have a total of 1000 shards
        MetricsProvider metricsProvider = new MetricsProvider(1000, null);
        metricsProvider.start(systemInfo, testState);
        metricsProvider.getMetricsObserver().subscribe(mockObserver);

        // Poke the internal state machine of the metrics provider.
        testState.onNext(STARTED);
        systemInfo.onNext(data);

        ArgumentCaptor<Metrics> captor = ArgumentCaptor.forClass(Metrics.class);
        verify(mockObserver).onNext(captor.capture());
        Metrics metrics1 = captor.getValue();
        Metrics metrics2 = metricsProvider.getMetrics();

        assertThat(metrics1).isEqualTo(metrics2);
    }

    @Test
    public void when_pushing_new_system_info__metrics_is_dumped_to_file() throws IOException {
        PublishSubject<Map<String, SystemInfo>> systemInfo = PublishSubject.create();
        PublishSubject<TestState> testState = PublishSubject.create();
        Observer<Metrics> mockObserver = mock(Observer.class);
        doNothing().when(mockObserver).onNext(any());

        SystemInfo mockNode = mock(SystemInfo.class);
        when(mockNode.getStoringPerShard()).thenReturn(100L);
        when(mockNode.getShardSize()).thenReturn(10L);
        when(mockNode.getStoredPerShard()).thenReturn(0.0);

        Map<String, SystemInfo> data = new HashMap<>();
        data.put("0.0.0.1", mockNode);

        MetricsProvider metricsProvider = new MetricsProvider(1000, TEST_DUMP_FILE_PATH);
        metricsProvider.start(systemInfo, testState);
        metricsProvider.getMetricsObserver().subscribe(mockObserver);

        // Poke the internal state machine of the metrics provider.
        testState.onNext(STARTED);
        systemInfo.onNext(data);

        ArgumentCaptor<Metrics> captor = ArgumentCaptor.forClass(Metrics.class);
        verify(mockObserver, timeout(100)).onNext(captor.capture());
        Metrics metrics = captor.getValue();

        List<String> lines = Files.readAllLines(TEST_DUMP_FILE_PATH);
        assertThat(lines.size()).isEqualTo(2); // title row + data row
        String[] fractions = lines.get(lines.size() - 1).split(",");

        assertThat(Long.toString(metrics.getTps())).isEqualTo(fractions[1]);
        assertThat(Long.toString(metrics.getProgress())).isEqualTo(fractions[2]);
        assertThat(Long.toString(metrics.getAverageTps())).isEqualTo(fractions[3]);
        assertThat(Long.toString(metrics.getPeakTps())).isEqualTo(fractions[4]);
    }

    @Test
    public void when_starting_metrics_provider__previous_metrics_value_is_restored() throws Exception {
        // timestamp, spotTps, progress, averageTps, peakTps
        String lines = "0, 1, 2, 3, 4\n1000,100,50,80,120";
        byte[] data = lines.getBytes(UTF_8);
        Files.write(TEST_DUMP_FILE_PATH, data, CREATE, WRITE);

        MetricsProvider metricsProvider = new MetricsProvider(1000, TEST_DUMP_FILE_PATH);
        metricsProvider.start(PublishSubject.create(), PublishSubject.create());
        Metrics metrics = metricsProvider.getMetrics();

        assertThat(metrics.getTps()).isEqualTo(100);
        assertThat(metrics.getProgress()).isEqualTo(50);
        assertThat(metrics.getAverageTps()).isEqualTo(80);
        assertThat(metrics.getPeakTps()).isEqualTo(120);
    }

}
