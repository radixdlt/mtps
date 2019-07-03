package org.radixdlt.explorer.system;

import io.reactivex.subjects.PublishSubject;
import org.junit.After;
import org.junit.Test;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.radixdlt.explorer.system.model.SystemInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.radixdlt.explorer.system.TestState.FINISHED;
import static org.radixdlt.explorer.system.TestState.STARTED;
import static org.radixdlt.explorer.system.TestState.TERMINATED;

public class TestStateProviderTest {
    private static final Path TEST_DUMP_FILE_PATH = Paths.get("test_state_provider_test.csv").toAbsolutePath();

    @After
    public void afterTest() throws Exception {
        Files.deleteIfExists(TEST_DUMP_FILE_PATH);
    }


    @Test
    public void when_starting_metrics_provider__previous_metrics_value_is_restored() throws IOException {
        String line = TERMINATED.name() + ",0,1";
        byte[] data = line.getBytes(UTF_8);
        Files.write(TEST_DUMP_FILE_PATH, data, CREATE, WRITE);

        TestStateProvider testStateProvider = new TestStateProvider(1000, 0.1f, TEST_DUMP_FILE_PATH);
        testStateProvider.start(PublishSubject.create(), PublishSubject.create());
        TestState testState = testStateProvider.getState();

        assertThat(testState).isEqualTo(TERMINATED);
        assertThat(testState.getStartTimestamp()).isEqualTo(0);
        assertThat(testState.getStopTimestamp()).isEqualTo(1);
    }

    @Test
    public void when_node_count_drops_below_threshold__finished_state_is_reported_back() throws IOException {
        // Define a known initial state
        String line = TERMINATED.name() + ",0,0";
        byte[] data = line.getBytes(UTF_8);
        Files.write(TEST_DUMP_FILE_PATH, data, CREATE, WRITE);

        // Start the test state provider and verify the initial state
        PublishSubject<Collection<NodeInfo>> nodes = PublishSubject.create();
        PublishSubject<Map<String, SystemInfo>> system = PublishSubject.create();
        TestStateProvider testStateProvider = new TestStateProvider(1000, 0.2f, TEST_DUMP_FILE_PATH);
        testStateProvider.start(nodes, system);
        assertThat(testStateProvider.getState()).isEqualTo(TERMINATED);

        // Set and verify the starting point state for this test
        nodes.onNext(getMockedTestNodes(10));
        system.onNext(getMockedSystemInfo(2, 1000));
        assertThat(testStateProvider.getState()).isEqualTo(STARTED);

        // Change the test and verify the expected state
        nodes.onNext(getMockedTestNodes(8));
        assertThat(testStateProvider.getState()).isEqualTo(FINISHED);
    }

    @Test
    public void when_node_count_stays_above_threshold__state_is_not_changed() throws IOException {
        // Define a known initial state
        String line = TERMINATED.name() + ",0,0";
        byte[] data = line.getBytes(UTF_8);
        Files.write(TEST_DUMP_FILE_PATH, data, CREATE, WRITE);

        // Start the test state provider and verify the internal state
        PublishSubject<Collection<NodeInfo>> nodes = PublishSubject.create();
        PublishSubject<Map<String, SystemInfo>> system = PublishSubject.create();
        TestStateProvider testStateProvider = new TestStateProvider(1000, 0.2f, TEST_DUMP_FILE_PATH);
        testStateProvider.start(nodes, system);
        assertThat(testStateProvider.getState()).isEqualTo(TERMINATED);

        // Set and verify the starting point state for this test
        nodes.onNext(getMockedTestNodes(10));
        system.onNext(getMockedSystemInfo(2, 1000));
        assertThat(testStateProvider.getState()).isEqualTo(STARTED);

        // Change the test and verify the expected state
        nodes.onNext(getMockedTestNodes(9));
        assertThat(testStateProvider.getState()).isEqualTo(STARTED);
    }


    private Collection<NodeInfo> getMockedTestNodes(int count) {
        Collection<NodeInfo> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(mock(NodeInfo.class));
        }
        return result;
    }

    private Map<String, SystemInfo> getMockedSystemInfo(int count, long storingPerShard) {
        Map<String, SystemInfo> result = new HashMap<>();
        for (int i = 0; i < count; i++) {
            SystemInfo mockedSystemInfo = mock(SystemInfo.class);
            when(mockedSystemInfo.getStoringPerShard()).thenReturn(storingPerShard);
            result.put(Integer.toString(i), mockedSystemInfo);
        }
        return result;
    }
}
