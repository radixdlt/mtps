package org.radixdlt.millionaire;

import com.radixdlt.client.core.crypto.ECSignature;
import org.bitcoin.NativeSecp256k1;
import org.bitcoin.NativeSecp256k1Util.AssertFailException;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

class Libsecp256k1KeyHandler implements KeyHandler {
    // Pubkey lengths and types
    private static final int  PUBKEY_UNCOMPRESSED_LEN  = 65;
	private static final int  PUBKEY_COMPRESSED_LEN    = 33;
	private static final byte PUBKEY_COMPRESSED_Y_EVEN = 0x02;
	private static final byte PUBKEY_COMPRESSED_Y_ODD  = 0x03;

	Libsecp256k1KeyHandler(Random secureRandom) throws AssertFailException {
		byte[] seed = new byte[32];
		secureRandom.nextBytes(seed);
		NativeSecp256k1.randomize(seed);
	}

	@Override
	public ECSignature sign(byte[] hash, byte[] privateKey) throws CryptoException {
		try {
			byte[] sig = NativeSecp256k1.sign(hash, extendPrivateKey(privateKey));
			return derToECSignature(sig);
		} catch (AssertFailException e) {
			throw new CryptoException(e);
		}
	}

	@Override
	public boolean verify(byte[] hash, ECSignature signature, byte[] publicKey) throws CryptoException {
		try {
			byte[] dersig = ecSignatureToDer(signature);
			// Note that libsecp256k1 will not verify signatures as valid
			// unless they have low-S.
			return NativeSecp256k1.verify(hash, dersig, publicKey);
		} catch (AssertFailException e) {
			throw new CryptoException(e);
		}
	}


	@Override
	public byte[] computePublicKey(byte[] privateKey) throws CryptoException {
		try {
			return compress(NativeSecp256k1.computePubkey(extendPrivateKey(privateKey)));
		} catch (AssertFailException ex) {
			throw new CryptoException(ex);
		}
	}

	private static ECSignature derToECSignature(byte[] dersig) {
		checkEquals(0x30, dersig[0] & 0xFF);
		checkEquals(dersig.length - 2, dersig[1] & 0xFF);
		checkEquals(0x02, dersig[2] & 0xFF);

		int rlen = dersig[3] & 0xFF;
		BigInteger r = new BigInteger(1, Arrays.copyOfRange(dersig, 4, 4 + rlen));

		checkEquals(0x02, dersig[rlen + 4]);
		int slen = dersig[rlen + 5] & 0xFF;
		BigInteger s = new BigInteger(1, Arrays.copyOfRange(dersig, rlen + 6, rlen + 6 + slen));

		return new ECSignature(r, s);
	}

	private static byte[] ecSignatureToDer(ECSignature ecsig) {
		final byte[] r = ecsig.getR().toByteArray();
		final int rlen = r.length;
		final byte[] s = ecsig.getS().toByteArray();
		final int slen = s.length;

		final byte[] derSig = new byte[rlen + slen + 6];
		derSig[0] = 0x30;
		derSig[1] = (byte) (derSig.length - 2);
		derSig[2] = 0x02;
		derSig[3] = (byte) rlen;
		System.arraycopy(r, 0, derSig, 4, rlen);
		derSig[rlen + 4] = 0x02;
		derSig[rlen + 5] = (byte) slen;
		System.arraycopy(s, 0, derSig, rlen + 6, slen);

		return derSig;
	}

	private static void checkEquals(int expected, int actual) {
		if (expected != actual) {
			throw new IllegalStateException("Expected value " + expected + ", but found " + actual);
		}
	}

	private byte[] extendPrivateKey(byte[] privKey) {
		if (privKey.length < 32) {
			byte[] newBytes = new byte[32];
			System.arraycopy(privKey, 0, newBytes, 32 - privKey.length, privKey.length);
			return newBytes;
		}
		return privKey;
	}

    private static byte[] compress(byte[] pubKey) {
    	if (pubKey.length == PUBKEY_UNCOMPRESSED_LEN) {
    		byte[] newBytes = new byte[PUBKEY_COMPRESSED_LEN];
    		newBytes[0] = (pubKey[pubKey.length - 1] & 0x01) != 0 ? PUBKEY_COMPRESSED_Y_ODD : PUBKEY_COMPRESSED_Y_EVEN;
    		System.arraycopy(pubKey, 1, newBytes, 1, PUBKEY_COMPRESSED_LEN - 1);
    		return newBytes;
    	}
    	return pubKey;
    }
}
