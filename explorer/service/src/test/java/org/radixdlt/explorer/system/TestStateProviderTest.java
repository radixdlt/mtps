package org.radixdlt.explorer.system;

import io.reactivex.subjects.PublishSubject;
import org.junit.AfterClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.radixdlt.explorer.system.TestState.TERMINATED;

public class TestStateProviderTest {
    private static final Path TEST_DUMP_FILE_PATH = Paths.get("test_state_provider_test.csv");

    @AfterClass
    public static void afterSuite() throws Exception {
        Files.deleteIfExists(TEST_DUMP_FILE_PATH);
    }


    @Test
    public void when_starting_metrics_provider__previous_metrics_value_is_restored() throws Exception {
        byte[] data = TERMINATED.name().getBytes(UTF_8);
        Files.write(TEST_DUMP_FILE_PATH, data, CREATE, WRITE);

        TestStateProvider testStateProvider = new TestStateProvider(1000, TEST_DUMP_FILE_PATH);
        testStateProvider.start(PublishSubject.create(), PublishSubject.create());
        TestState testState = testStateProvider.getState();

        assertThat(testState).isEqualTo(TERMINATED);
    }

}
