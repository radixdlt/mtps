package org.radixdlt.explorer.transactions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import org.radix.serialization2.client.Serialize;
import org.radix.utils.UInt256;
import org.radixdlt.explorer.config.Configuration;
import org.radixdlt.explorer.error.IllegalAddressException;
import org.radixdlt.explorer.helper.BitcoinAddressHelper;
import org.radixdlt.explorer.helper.DataHelper;
import org.radixdlt.explorer.helper.model.HttpResponseData;
import org.radixdlt.explorer.nodes.model.NodeInfo;
import org.radixdlt.explorer.transactions.model.TransactionInfo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.radixdlt.client.core.atoms.particles.Spin.DOWN;
import static com.radixdlt.client.core.atoms.particles.Spin.UP;

/**
 * Enables means of getting transactions data from the Radix network.
 */
class TransactionsProvider {
    private final CompositeDisposable disposables;
    private final Collection<NodeInfo> nodes;
    private final Object nodesUpdateLock;
    private final BitcoinAddressHelper bitcoinHelper;
    private final DataHelper dataHelper;
    private final int pageSize;

    private boolean isStarted;

    /**
     * Creates a new instance of this class.
     *
     * @param dataHelper    The helper object to request remote data with.
     * @param bitcoinHelper The Bitcoin to Radix address helper.
     * @param pageSize      The max size of any paged data.
     */
    TransactionsProvider(DataHelper dataHelper, BitcoinAddressHelper bitcoinHelper, int pageSize) {
        this.bitcoinHelper = bitcoinHelper;
        this.dataHelper = dataHelper;
        this.disposables = new CompositeDisposable();
        this.nodes = ConcurrentHashMap.newKeySet();
        this.nodesUpdateLock = new Object();
        this.pageSize = pageSize;
        this.isStarted = false;
    }

    /**
     * Starts monitoring the provided nodes. A shard can be derived
     * directly from a Radix address and that shard can be mapped to
     * a node. This is the reason why we're monitoring the nodes in
     * "real-time" here.
     *
     * @param nodesObserver The callback that provides information of
     *                      node changes.
     */
    synchronized void start(Observable<Collection<NodeInfo>> nodesObserver) {
        if (!isStarted) {
            isStarted = true;
            disposables.add(nodesObserver.subscribe(this::updateNodes));
        }
    }

    /**
     * Stops the calculation of throughput metrics.
     */
    synchronized void stop() {
        if (isStarted) {
            isStarted = false;
            disposables.clear();
        }
    }

    /**
     * Fetches the transactions on a given address from a node serving that shard.
     *
     * @param bitcoinAddress The Bitcoin address for which to fetch transactions.
     * @param page           The page of transactions to get (static page size).
     * @return The callback eventually providing a list of transactions.
     */
    Single<TransactionInfo[]> getTransactionsObserver(String bitcoinAddress, int page) {
        int magic = Configuration.getInstance().getUniverseMagic();
        RadixAddress radixAddress = bitcoinHelper.getRadixAddress(magic, bitcoinAddress);

        if (radixAddress == null) {
            return Single.error(new IllegalAddressException(
                    "Couldn't generate Radix address from Bitcoin address: " + bitcoinAddress));
        }

        long shard = radixAddress.getUID().getShard();
        String host = getNodeForShard(shard);
        if (host == null || host.isEmpty()) {
            return Single.error(new IllegalAddressException(
                    "Couldn't find node serving address [Bitcoin, Radix, magic, shard]: " +
                            bitcoinAddress + ", " + radixAddress.toString() + ", " + magic + ", " + shard));
        }

        // Convert from 1-based Explorer page to 0-based node index.
        int index = Math.max(0, page - 1) * pageSize;

        // TODO: Refactor this as soon as there is time available
        //       There is currently a hard dependency to both Gson
        //       and Radix Serialization with this implementation.
        return dataHelper
                .getRawAuthorized(host, "api/atoms?address=%s&index=%d&limit=%d",
                        radixAddress.toString(), index, pageSize)                   // Fetch raw JSON data
                .filter(HttpResponseData::isJson)                                   // Verify it's JSON
                .map(response -> new JsonParser().parse(response.getContent()))     // Parse JSON into Gson JsonElement
                .filter(Objects::nonNull)                                           // Verify parse success
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(jsonObject -> jsonObject.has("data"))                       // Verify "data" property exists
                .map(jsonObject -> jsonObject.get("data"))
                .filter(JsonElement::isJsonArray)
                .map(JsonElement::getAsJsonArray)
                .map(JsonArray::iterator)
                .flatMapObservable(iterator -> Observable.fromIterable(() -> iterator))
                .filter(Objects::nonNull)
                .map(jsonElement -> Serialize.getInstance().fromJson(jsonElement.toString(), Atom.class))
                .onErrorReturn(throwable -> null)
                .filter(Objects::nonNull)
                .map(atom -> extractTransactionInfo(atom, radixAddress))
                .filter(Objects::nonNull)
                .toList()
                .map(list -> list.toArray(new TransactionInfo[0]));
    }

    /**
     * Extracts the aggregated total of transacted value to and/or from
     * a particular address in a given atom.
     *
     * @param atom      The atom to extract the transacted value from.
     * @param myAddress The address to aggregate value on.
     * @return Information on the collected transfer.
     */
    private TransactionInfo extractTransactionInfo(Atom atom, RadixAddress myAddress) {
        if (atom == null) {
            return null;
        }

        Map<String, String> metaData = atom.getMetaData();
        String txId = Optional.of(metaData.get("btcTxId")).orElse("").replaceFirst(":str:", "");
        String time = Optional.of(metaData.get("timestamp")).orElse("0").replaceFirst(":str:", "");

        UInt256 ups = atom.particles(UP)
                .map(particle -> (TransferrableTokensParticle) particle)
                .filter(particle -> myAddress == null || myAddress.ownsKey(particle.getAddress().getPublicKey()))
                .map(TransferrableTokensParticle::getAmount)
                .reduce(UInt256.ZERO, UInt256::add);

        UInt256 downs = atom.particles(DOWN)
                .map(particle -> (TransferrableTokensParticle) particle)
                .filter(particle -> myAddress == null || myAddress.ownsKey(particle.getAddress().getPublicKey()))
                .map(TransferrableTokensParticle::getAmount)
                .reduce(UInt256.ZERO, UInt256::add);

        UInt256 total = ups.subtract(downs);
        BigDecimal grandTotal = TokenUnitConversions.subunitsToUnits(total);
        return new TransactionInfo(txId, Long.valueOf(time), grandTotal);
    }

    /**
     * Sets the internal cache to hold the provided nodes.
     *
     * @param newNodes The new nodes.
     */
    private void updateNodes(Collection<NodeInfo> newNodes) {
        synchronized (nodesUpdateLock) {
            nodes.clear();
            nodes.addAll(newNodes);
        }
    }

    /**
     * Searches for a node serving the provided shard.
     *
     * @param shard The shard the node needs to serve.
     * @return A node or null.
     */
    private String getNodeForShard(long shard) {
        return nodes.stream()
                .filter(info -> info.isOverlappedBy(shard))
                .map(NodeInfo::getAddress)
                .findFirst()
                .orElse(null);
    }

}
