package org.radixdlt.explorer;

import org.radixdlt.explorer.config.Configuration;
import org.radixdlt.explorer.error.ExplorerClientErrorHandler;
import org.radixdlt.explorer.error.ExplorerServerErrorHandler;
import org.radixdlt.explorer.helper.BitcoinAddressHelper;
import org.radixdlt.explorer.helper.DataHelper;
import org.radixdlt.explorer.helper.HttpClient;
import org.radixdlt.explorer.helper.JsonParser;
import org.radixdlt.explorer.helper.gson.GsonParser;
import org.radixdlt.explorer.helper.okhttp.OkHttpClient;
import org.radixdlt.explorer.metrics.MetricsGetHandler;
import org.radixdlt.explorer.metrics.MetricsService;
import org.radixdlt.explorer.nodes.NodeService;
import org.radixdlt.explorer.system.SystemInfoService;
import org.radixdlt.explorer.system.TestStateService;
import org.radixdlt.explorer.transactions.TransactionsGetHandler;
import org.radixdlt.explorer.transactions.TransactionsService;
import org.radixdlt.explorer.validation.AddressValidator;
import org.radixdlt.explorer.validation.PageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.error.ClientErrorHandler;
import ratpack.error.ServerErrorHandler;
import ratpack.rx2.RxRatpack;
import ratpack.server.RatpackServer;
import ratpack.service.ServiceDependencies;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Exposes the main entry point for the Explorer web service.
 */
public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");

    // Make sure the RxJava bindings are initialized.
    // This is only needed once per JVM.
    static {
        RxRatpack.initialize();
    }


    /**
     * Exposes the main entry point for the Application service.
     *
     * @param args Any caller provided arguments.
     * @throws Exception If something goes wrong.
     */
    public static void main(String[] args) throws Exception {
        ensureProperties();
        ensureResources();

        DataHelper dataHelper = buildDataHelper();
        RatpackServer.start(server -> server
                .serverConfig(config -> config.baseDir(new File("").getAbsoluteFile()))
                .registryOf(action -> action
                        .add(NodeService.class, buildNodeService(dataHelper))
                        .add(MetricsService.class, buildMetricsService())
                        .add(SystemInfoService.class, buildSystemInfoService(dataHelper))
                        .add(TestStateService.class, buildTestStateService())
                        .add(TransactionsService.class, buildTransactionsService(dataHelper))
                        .add(ServiceDependencies.class, spec -> spec
                                .dependsOn(SystemInfoService.class, NodeService.class)
                                .dependsOn(MetricsService.class, SystemInfoService.class)
                                .dependsOn(MetricsService.class, TestStateService.class)
                                .dependsOn(TransactionsService.class, NodeService.class)
                                .dependsOn(TestStateService.class, NodeService.class)
                                .dependsOn(TestStateService.class, SystemInfoService.class)))
                .handlers(chain -> chain
                        .register(registry -> registry
                                .add(AddressValidator.class, new AddressValidator())
                                .add(PageValidator.class, new PageValidator())
                                .add(ServerErrorHandler.class, new ExplorerServerErrorHandler())
                                .add(ClientErrorHandler.class, new ExplorerClientErrorHandler()))
                        .files(files -> files
                                .dir("www")
                                .indexFiles("index.html"))
                        .path("api/metrics", path -> path.byMethod(method -> method
                                .get(new MetricsGetHandler())))
                        .path("api/transactions/:address", path -> path.byMethod(method -> method
                                .get(new TransactionsGetHandler())))));

        LOGGER.info("Lets get ready to rumbleeeeeee!!!");
    }

    /**
     * Ensures there is a 'config.properties' file in the runtime root
     * directory. If not, a default properties file is copied from the
     * java resources.
     */
    private static void ensureProperties() {
        Path configFilePath = Paths.get(Configuration.CONFIG_FILE);
        if (!Files.exists(configFilePath)) {
            try (InputStream source = Application.class.getResourceAsStream("/config.properties")) {
                Files.copy(source, configFilePath);
            } catch (IOException e) {
                throw new UncheckedIOException("Couldn't read configuration properties", e);
            }
        }
    }

    /**
     * Ensures there is a 'www' subdirectory with static file resources
     * in the runtime root. If not, a default resource is extracted and
     * copied from the java resources.
     */
    private static void ensureResources() {
        if (!Files.exists(Paths.get("www")) && Application.class.getResource("/www.zip") != null) {
            try (InputStream source = Application.class.getResourceAsStream("/www.zip");
                 ZipInputStream zipStream = new ZipInputStream(source)) {

                ZipEntry zipEntry;
                while ((zipEntry = zipStream.getNextEntry()) != null) {
                    if (zipEntry.isDirectory()) {
                        Files.createDirectories(Paths.get(zipEntry.getName()));
                    } else {
                        Files.copy(zipStream, Paths.get(zipEntry.getName()));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Couldn't read www.zip resource", e);
            }
        }
    }

    /**
     * Builds a new, default data helper instance.
     *
     * @return The new {@link DataHelper}.
     */
    private static DataHelper buildDataHelper() {
        Configuration configuration = Configuration.getInstance();
        String username = configuration.getNodesAuthUsername();
        String password = configuration.getNodesAuthPassword();
        long timeout = configuration.getMetricsCalculationInterval();
        HttpClient httpClient = new OkHttpClient(timeout, username, password);
        JsonParser jsonParser = new GsonParser();
        return new DataHelper(httpClient, jsonParser);
    }

    /**
     * Builds a new node service instance.
     *
     * @param dataHelper The data helper to assign to the new service.
     * @return The new {@link NodeService}.
     */
    private static NodeService buildNodeService(DataHelper dataHelper) {
        Configuration configuration = Configuration.getInstance();
        String nodeFinderUrl = configuration.getNodesUrl();
        long nodesRefreshInterval = configuration.getNodesRefreshInterval();
        float nodesSubsetFraction = configuration.getNodesSubsetFraction();
        int nodesMaxCount = configuration.getMaxNodesSubsetCount();
        return new NodeService(dataHelper, nodeFinderUrl, nodesRefreshInterval, nodesSubsetFraction, nodesMaxCount);
    }

    /**
     * Builds a new system info service instance.
     *
     * @param dataHelper The data helper to assign to the new service.
     * @return The new {@link SystemInfoService}.
     */
    private static SystemInfoService buildSystemInfoService(DataHelper dataHelper) {
        Configuration configuration = Configuration.getInstance();
        long refreshInterval = configuration.getMetricsCalculationInterval();
        return new SystemInfoService(dataHelper, refreshInterval);
    }

    /**
     * Builds a new metrics service instance.
     *
     * @return The new {@link MetricsService}.
     */
    private static MetricsService buildMetricsService() {
        Configuration configuration = Configuration.getInstance();
        long universeShardCount = configuration.getUniverseShardCount();
        Path dumpFilePath = configuration.getMetricsDumpFilePath();
        return new MetricsService(universeShardCount, dumpFilePath);
    }

    /**
     * Builds a new test state service instance.
     *
     * @return The new {@link TestStateService}.
     */
    private static TestStateService buildTestStateService() {
        Configuration configuration = Configuration.getInstance();
        int runningThreshold = configuration.getTestRunningThreshold();
        Path stateDumpPath = configuration.getTestStateDumpFilePath();
        return new TestStateService(runningThreshold, stateDumpPath);
    }

    /**
     * Builds a new transactions service instance.
     *
     * @param dataHelper The data helper to assign to the new service.
     * @return The new {@link TransactionsService}.
     */
    private static TransactionsService buildTransactionsService(DataHelper dataHelper) {
        Configuration configuration = Configuration.getInstance();
        int pageSize = configuration.getTransactionsPageSize();
        BitcoinAddressHelper bitcoinHelper = new BitcoinAddressHelper();
        return new TransactionsService(dataHelper, bitcoinHelper, pageSize);
    }

}
