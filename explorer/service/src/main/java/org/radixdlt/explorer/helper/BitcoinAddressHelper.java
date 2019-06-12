package org.radixdlt.explorer.helper;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.crypto.ECKeyPair;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.radix.crypto.Hash;

public class BitcoinAddressHelper {
    private static final int PRIVATE_KEY_MINIMUM_LENGTH = 32;

    /**
     * Parses and converts a Base58 encoded Bitcoin address into a
     * corresponding Radix key pair.
     *
     * @param radixUniverseMagic          The magic of the universe.
     * @param base58EncodedBitcoinAddress The Bitcoin address
     * @return The Radix key pair.
     */
    public RadixAddress getRadixAddress(int radixUniverseMagic, String base58EncodedBitcoinAddress) {
        NetworkParameters networkParameters = new MainNetParams();
        Address bitcoinAddress = Address.fromString(networkParameters, base58EncodedBitcoinAddress);
        byte[] bytes = Hash.sha256(bitcoinAddress.getHash());
        byte[] privateKey = ensurePrivateKeySize(bytes);

        ECKeyPair ecKeyPair = new ECKeyPair(privateKey);
        return new RadixAddress(radixUniverseMagic, ecKeyPair.getPublicKey());
    }

    /**
     * Stretches out a too short private key with leading zeroes. Longer
     * private keys are left untouched.
     *
     * @param privateKey The private key to ensure a minimum length of.
     * @return A private key of valid length.
     */
    private byte[] ensurePrivateKeySize(byte[] privateKey) {
        byte[] result = privateKey;

        if (privateKey.length < PRIVATE_KEY_MINIMUM_LENGTH) {
            result = new byte[PRIVATE_KEY_MINIMUM_LENGTH];
            int size = privateKey.length;
            int start = PRIVATE_KEY_MINIMUM_LENGTH - size;
            System.arraycopy(privateKey, 0, result, start, size);
        }

        return result;
    }

}
