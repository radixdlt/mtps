package org.radixdlt.explorer.transactions.model;

import java.math.BigDecimal;

/**
 * Holds information on a successfully imported Bitcoin transactions.
 */
public class TransactionInfo {
    private final String bitcoinTransactionId;
    private final long bitcoinBlockTimestamp;
    private final BigDecimal amount;

    /**
     * Creates a new transaction object.
     *
     * @param bitcoinTransactionId  The Bitcoin transaction hash.
     * @param bitcoinBlockTimestamp The Bitcoin block timestamp.
     * @param amount                The transferred amount.
     */
    public TransactionInfo(String bitcoinTransactionId, long bitcoinBlockTimestamp, BigDecimal amount) {
        this.bitcoinTransactionId = bitcoinTransactionId;
        this.bitcoinBlockTimestamp = bitcoinBlockTimestamp;
        this.amount = amount;
    }


    /**
     * @return The transaction id of the original Bitcoin transaction.
     */
    public String getBitcoinTransactionId() {
        return bitcoinTransactionId;
    }

    /**
     * @return The timestamp of the Bitcoin block containing the original
     * transaction.
     */
    public long getBitcoinBlockTimestamp() {
        return bitcoinBlockTimestamp;
    }

    /**
     * @return The amount of Bitcoins that were transferred.
     */
    public BigDecimal getAmount() {
        return amount;
    }

}
