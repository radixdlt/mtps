package org.radixdlt.explorer.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class DumpHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final ExecutorService dumpExecutor;


    public DumpHelper() {
        this.dumpExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Shuts down the dump executor, allowing the currently executing
     * task to finish, but not starting any new tasks.
     */
    public void stopDumpExecutor() {
        dumpExecutor.shutdownNow();
    }

    /**
     * Makes an atomic (-ish) dump of the provided data to the provided
     * path.
     *
     * @param data The data to dump.
     * @param path The path to the file to dump it in (or null, in which
     *             case nothing is dumped.
     */
    public synchronized void dumpData(byte[] data, Path path) {
        if (path == null) {
            return;
        }

        try {
            dumpExecutor.submit(() -> {
                // Ensure parent folder exists
                path.toAbsolutePath().getParent().toFile().mkdirs();
                Path tmpFilePath = path.getParent().resolve("tmp.txt").toAbsolutePath();
                Path targetFilePath = path.toAbsolutePath();

                try (FileOutputStream fileOutputStream = new FileOutputStream(tmpFilePath.toString())) {
                    fileOutputStream.write(data);
                    fileOutputStream.flush();
                    fileOutputStream.getFD().sync();
                    Files.move(tmpFilePath, targetFilePath, ATOMIC_MOVE, REPLACE_EXISTING);
                } catch (Exception e) {
                    LOGGER.info("Couldn't dump data to file: " + path.toString(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.info("Couldn't enqueue dump data task. Ignoring this," +
                    " but you should look into it as the dump file may now be broken", e);
        }
    }

    /**
     * Reads the last line from the file at the provided path.
     *
     * @param path The path to the file to read from.
     * @return The last line in the provided file. May be empty
     * if the file was empty or null if something goes wrong.
     */
    public synchronized String restoreLastDumpedData(Path path) {
        if (path == null) {
            return null;
        }

        File file = path.toAbsolutePath().toFile();
        try (RandomAccessFile fileHandler = new RandomAccessFile(file, "r")) {
            long filePointer = fileHandler.length();
            long fileLength = filePointer - 1;
            StringBuilder sb = new StringBuilder();

            // Fast-forward past the end of the file
            // and start reading the bytes from the
            // end until the first (last) line feed
            // is encountered.
            while (--filePointer != -1) {
                fileHandler.seek(filePointer);
                int readByte = fileHandler.readByte();

                if (readByte == 0xA) { // 'new line'
                    if (filePointer == fileLength) {
                        continue;
                    }
                    break;
                } else if (readByte == 0xD) { // 'carriage return'
                    if (filePointer == fileLength - 1) {
                        continue;
                    }
                    break;
                }

                sb.append((char) readByte);
            }

            return sb.reverse().toString();
        } catch (Exception e) {
            LOGGER.info("Couldn't restore previously dumped data," +
                    " falling back to default", e);
            return null;
        }
    }

}
