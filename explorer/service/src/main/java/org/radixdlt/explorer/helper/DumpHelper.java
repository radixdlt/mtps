package org.radixdlt.explorer.helper;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Offers means of safely dumping data to a file on the filesystem. This
 * helper class enqueues any dump tasks in a single threaded executor
 * service and each dump is forcefully synced to the file system.
 */
public class DumpHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final ExecutorService dumpExecutor;
    private final Path dumpFilePath;


    public DumpHelper(Path dumpFilePath) {
        this.dumpExecutor = Executors.newSingleThreadExecutor();
        if (dumpFilePath != null) {
            this.dumpFilePath = dumpFilePath.toAbsolutePath();
            this.dumpFilePath.getParent().toFile().mkdirs();
        } else {
            this.dumpFilePath = null;
        }
    }

    /**
     * Shuts down the dump executor, allowing any submitted tasks to
     * finish executing within 10 seconds, but not accepting any new
     * tasks.
     *
     * @return A handle to the shutdown task that the caller can use
     * to detect when the shutdown has been finished and the success
     * state of it.
     */
    public Completable stop() {
        return Completable
                .fromCallable(() -> {
                    dumpExecutor.shutdown();
                    dumpExecutor.awaitTermination(10, SECONDS);
                    return null;
                })
                .subscribeOn(Schedulers.io());
    }

    /**
     * Submits an atomic dump-to-file task of the provided string.
     *
     * @param string The data to dump.
     * @return A handle to the dump task that the caller can use to detect
     * when the task has been finished and the success state of it.
     */
    public Completable dumpData(final String string) {
        if (dumpFilePath == null || string == null || string.isEmpty()) {
            return Completable.complete();
        }

        try {
            // Ensure trailing new-line
            String line = string.endsWith("\n") ? string : (string + "\n");
            byte[] data = line.getBytes(UTF_8);
            DumpTask task = new DumpTask(dumpFilePath, data);
            Future<?> future = dumpExecutor.submit(task);
            return Completable.fromFuture(future);
        } catch (RejectedExecutionException e) {
            LOGGER.info("Couldn't enqueue dump task", e);
            return Completable.error(e);
        }
    }

    /**
     * Reads the last line from the dump file.
     *
     * @return The last line in the dump file. May be an empty string if
     * the dump file was empty or no dump file was specified.
     */
    public Single<String> restoreData() {
        if (dumpFilePath == null) {
            return Single.just("");
        }

        try {
            RestoreTask task = new RestoreTask(dumpFilePath);
            Future<String> future = dumpExecutor.submit(task);
            return Single.fromFuture(future);
        } catch (RejectedExecutionException e) {
            LOGGER.info("Couldn't enqueue restore task", e);
            return Single.error(e);
        }
    }

    /**
     * Encapsulates the work that needs to be done when dumping data to
     * the dump file.
     */
    private static final class DumpTask implements Callable<Void> {
        private final Path path;
        private final byte[] data;

        private DumpTask(Path path, byte[] data) {
            this.path = path;
            this.data = data;
        }

        @Override
        public Void call() throws Exception {
            // Create a temporary file to work with in isolation.
            // Make sure we're appending to existing data, not to
            // an empty file.
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            if (Files.exists(path)) {
                Files.move(path, tmp, ATOMIC_MOVE, REPLACE_EXISTING);
            }

            // Now, append to temporary file and make sure all data
            // is flushed to the file sink and synchronized with the
            // file system. Once we're sure all bytes exist on disk,
            // we're ready to (atomically) update the persisted file.
            try (FileOutputStream fileOutputStream = new FileOutputStream(tmp.toString(), true)) {
                fileOutputStream.write(data);
                fileOutputStream.flush();
                fileOutputStream.getFD().sync();
                fileOutputStream.close();
                Files.move(tmp, path, ATOMIC_MOVE, REPLACE_EXISTING);
            }

            return null;
        }
    }

    /**
     * Encapsulates the work that needs to be done when restoring data
     * from the last line in the dump file.
     */
    private static final class RestoreTask implements Callable<String> {
        private final Path path;

        private RestoreTask(Path path) {
            this.path = path;
        }

        @Override
        public String call() throws Exception {
            File file = path.toFile();
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                long filePointer = randomAccessFile.length();
                long fileLength = filePointer - 1;
                StringBuilder sb = new StringBuilder();

                // Fast-forward past the end of the file and start reading
                // the bytes from the end until the first (last) line feed
                // is encountered.
                while (--filePointer != -1) {
                    randomAccessFile.seek(filePointer);
                    int readByte = randomAccessFile.readByte();
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
            }
        }
    }


}
