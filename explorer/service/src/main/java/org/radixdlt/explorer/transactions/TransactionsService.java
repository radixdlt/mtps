package org.radixdlt.explorer.transactions;

import io.reactivex.Single;
import org.radixdlt.explorer.helper.BitcoinAddressHelper;
import org.radixdlt.explorer.helper.DataHelper;
import org.radixdlt.explorer.nodes.NodeService;
import org.radixdlt.explorer.transactions.model.TransactionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ratpack.registry.Registry;
import ratpack.service.Service;
import ratpack.service.StartEvent;
import ratpack.service.StopEvent;

public class TransactionsService implements Service {
    private static final Logger LOGGER = LoggerFactory.getLogger("org.radixdlt.explorer");
    private final TransactionsProvider transactionsProvider;

    public TransactionsService(DataHelper dataHelper, BitcoinAddressHelper bitcoinHelper, int pageSize) {
        transactionsProvider = new TransactionsProvider(dataHelper, bitcoinHelper, pageSize);
    }

    @Override
    public void onStart(StartEvent event) {
        Registry registry = event.getRegistry();
        NodeService nodeService = registry.get(NodeService.class);
        transactionsProvider.start(nodeService.getNodeObserver());
        LOGGER.info("Transactions service started successfully");
    }

    @Override
    public void onStop(StopEvent event) {
        transactionsProvider.stop();
        LOGGER.info("Transactions service stopped successfully");
    }

    public Single<TransactionInfo[]> getTransactionsObserver(String address, int page) {
        return transactionsProvider.getTransactionsObserver(address, page);
    }

}
