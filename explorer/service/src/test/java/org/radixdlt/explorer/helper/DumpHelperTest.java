package org.radixdlt.explorer.helper;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DumpHelperTest {
    private static final Path PATH = Paths.get("dump_helper_test.txt");


    @After
    public void afterTest() throws IOException {
        Files.deleteIfExists(PATH);
    }


    @Test
    public void when_dumping_data_to_file__it_is_appended_to_the_end() throws Exception {
        DumpHelper dumpHelper = new DumpHelper();
        dumpHelper.dumpData("first\n".getBytes(), PATH);
        dumpHelper.dumpData("second\n".getBytes(), PATH).get();

        List<String> lines = Files.readAllLines(PATH);
        assertThat(lines.size()).isEqualTo(2);
        assertThat(lines.get(0)).isEqualTo("first");
        assertThat(lines.get(1)).isEqualTo("second");
    }

    @Test
    public void when_restoring_dumped_data__the_last_line_is_returned() throws Exception {
        DumpHelper dumpHelper = new DumpHelper();
        dumpHelper.dumpData("first\n".getBytes(), PATH).get();
        dumpHelper.dumpData("second\n".getBytes(), PATH).get();

        String data = dumpHelper.restoreLastDumpedData(PATH);
        assertThat(data).isEqualTo("second");
    }
}
