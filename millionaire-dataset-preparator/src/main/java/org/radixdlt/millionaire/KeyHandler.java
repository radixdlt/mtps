package org.radixdlt.millionaire;

import com.radixdlt.client.core.crypto.ECSignature;

// All methods must be thread safe
interface KeyHandler {
	ECSignature sign(byte[] hash, byte[] privateKey) throws CryptoException;
	boolean verify(byte[] hash, ECSignature signature, byte[] publicKey) throws CryptoException;
	byte[] computePublicKey(byte[] privateKey) throws CryptoException;
}
