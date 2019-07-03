package org.radixdlt.explorer.helper;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public class DumpHelperTest {
    private static final Path PATH = Paths.get("dump_helper_test.txt");

    @After
    public void afterTest() throws IOException {
        Files.deleteIfExists(PATH);
    }


    @Test
    public void when_dumping_data_to_file__it_is_appended_to_the_end() throws IOException {
        DumpHelper dumpHelper = new DumpHelper(PATH);
        dumpHelper.dumpData("first");
        dumpHelper.dumpData("second").blockingAwait();

        List<String> lines = Files.readAllLines(PATH);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("second");
    }

    @Test
    public void when_clearing_data_before_dump__previous_data_is_removed_from_file() throws IOException {
        DumpHelper dumpHelper = new DumpHelper(PATH);
        dumpHelper.dumpData("first");
        dumpHelper.dumpData("second");
        dumpHelper.dumpData("third", true);
        dumpHelper.dumpData("fourth").blockingAwait();

        List<String> lines = Files.readAllLines(PATH);
        assertThat(lines.size()).isEqualTo(2);
        assertThat(lines.get(0)).isEqualTo("third");
        assertThat(lines.get(1)).isEqualTo("fourth");
    }

    @Test
    public void when_restoring_dumped_data__the_last_line_is_returned() {
        DumpHelper dumpHelper = new DumpHelper(PATH);
        dumpHelper.dumpData("1");
        dumpHelper.dumpData("2");

        String data = dumpHelper.restoreData().blockingGet();
        assertThat(data).isEqualTo("2");
    }

    @Test
    public void when_submitting_tasks__the_submit_order_is_honored() throws IOException {
        DumpHelper dumpHelper = new DumpHelper(PATH);
        dumpHelper.dumpData("zero");
        dumpHelper.dumpData("eins");
        String snapshot = dumpHelper.restoreData().blockingGet();
        dumpHelper.dumpData("zwei");
        dumpHelper.dumpData("drei").blockingAwait();

        List<String> lines = Files.readAllLines(PATH);
        assertThat(lines.size()).isEqualTo(4);
        assertThat(lines.get(0)).isEqualTo("zero");
        assertThat(lines.get(1)).isEqualTo("eins");
        assertThat(lines.get(2)).isEqualTo("zwei");
        assertThat(lines.get(3)).isEqualTo("drei");

        assertThat(snapshot).isEqualTo("eins");
    }

    @Test
    public void after_dump_helper_is_stopped__no_more_tasks_can_be_submitted() {
        DumpHelper dumpHelper = new DumpHelper(PATH);
        dumpHelper.stop().blockingAwait();
        Throwable error = dumpHelper.dumpData("ett").blockingGet();
        assertThat(error).isInstanceOf(RejectedExecutionException.class);
    }

}
