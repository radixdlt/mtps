package org.radixdlt.explorer.validation;

import org.radixdlt.explorer.error.IllegalAddressException;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;

import static java.math.BigInteger.ZERO;

/**
 * Offers a convenient method for Bitcoin address validation.
 */
public class AddressValidator implements Validator<String, String> {
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger FIFTY_EIGHT = BigInteger.valueOf(58);

    @Override
    public String validate(String address) throws IllegalArgumentException {
        if (address == null) {
            throw new IllegalAddressException("Address must not be empty");
        }

        int length = address.length();
        if (length < 26 || length > 35) {
            throw new IllegalAddressException("Unexpected length: " + length);
        }

        byte[] decoded = decode(address);
        byte[] raw = Arrays.copyOfRange(decoded, 0, 21);
        byte[] hashed = hash(raw);
        byte[] hashedAgain = hash(hashed);
        byte[] checksum1 = Arrays.copyOfRange(decoded, 21, 25);
        byte[] checksum2 = Arrays.copyOfRange(hashedAgain, 0, 4);

        if (!Arrays.equals(checksum1, checksum2)) {
            throw new IllegalAddressException("Illegal address: " + address);
        }

        return address;
    }

    /**
     * Performs a SHA-256 hash on a given byte array.
     *
     * @param data The data to hash.
     * @return The hashed result.
     */
    private byte[] hash(byte[] data) {
        try {
            return MessageDigest
                    .getInstance("SHA-256")
                    .digest(data);
        } catch (Exception e) {
            throw new IllegalAddressException("Couldn't hash data", e);
        }
    }

    /**
     * Tries to decode a Base58 encoded Bitcoin address into a 25 bytes long byte array.
     *
     * @param address The address to decode.
     * @return The decoded byte array.
     */
    private byte[] decode(String address) {
        if (address == null || address.isEmpty()) {
            throw new IllegalAddressException("Unexpected empty address");
        }

        try {
            byte[] bytes = address.chars()
                    .mapToObj(BASE58_ALPHABET::indexOf)
                    .map(BigInteger::valueOf)
                    .reduce(ZERO, (a, b) -> a.multiply(FIFTY_EIGHT).add(b))
                    .toByteArray();

            byte[] result = new byte[25];
            System.arraycopy(bytes, 0, result, result.length - bytes.length, bytes.length);

            return result;
        } catch (Exception e) {
            throw new IllegalAddressException("Couldn't decode address: " + address, e);
        }
    }

}
