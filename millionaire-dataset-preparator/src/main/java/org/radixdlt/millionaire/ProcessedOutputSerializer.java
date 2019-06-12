package org.radixdlt.millionaire;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.crypto.ECKeyPair;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.client.Serialize;
import org.radix.utils.UInt256;

import java.io.IOException;
import java.io.Serializable;

public class ProcessedOutputSerializer implements Serializer<ProcessedOutput>, Serializable {
    private static ProcessedOutputSerializer serializer;
    private RRI tokenReference;

    ProcessedOutputSerializer(RRI tokenReference) {
        this.tokenReference = tokenReference;
    }

    public static void initStatic(RRI tokenReference) {
        serializer = new ProcessedOutputSerializer(tokenReference);
    }


    @Override
    public void serialize(@NotNull DataOutput2 out, @NotNull ProcessedOutput value) throws IOException {
        // Owner
        byte[] pk = value.owner.getPrivateKey();
        out.writeInt(pk.length);
        out.write(pk);

        byte[] publicKey = value.owner.getPublicKey().getPublicKey();
        out.writeInt(publicKey.length);
        out.write(publicKey);

        // Particle
//        byte[] serializedParticle = Serialize.getInstance().toDson(value.particle, DsonOutput.Output.PERSIST);
//        out.writeInt(serializedParticle.length);
//        out.write(serializedParticle);

        // Nonce
        out.writeLong(value.particle.getNonce());
        // Planck
        out.writeLong(value.particle.getPlanck());
        // Amount
        byte[] amount = value.particle.getAmount().toByteArray();
        out.writeInt(amount.length);
        out.write(amount);
        // address
        out.writeUTF(value.particle.getAddress().toString());

    }

    @Override
    public ProcessedOutput deserialize(@NotNull DataInput2 input, int available) throws IOException {
        // Owner
        int pkLen = input.readInt();
        byte[] pk = new byte[pkLen];
        input.readFully(pk, 0, pkLen);

        int publicLen = input.readInt();
        byte[] publicKey = new byte[publicLen];
        input.readFully(publicKey, 0, publicLen);

        ECKeyPair owner = new ECKeyPair(publicKey, pk);

        // Particle
//        int particleLength = input.readInt();
//        byte[] serializedParticle = new byte[particleLength];
//        input.readFully(serializedParticle, 0, particleLength);
//        TransferrableTokensParticle particle = Serialize.getInstance().fromDson(serializedParticle, TransferrableTokensParticle.class);


        // Nonce
        long nonce = input.readLong();
        // Planck
        long planck = input.readLong();
        // Amount
        int l = input.readInt();
        byte[] amountBytes = new byte[l];
        input.readFully(amountBytes, 0, l);
        UInt256 amount = UInt256.from(amountBytes);
        // address
        RadixAddress address = RadixAddress.from(input.readUTF());

        TransferrableTokensParticle particle = new TransferrableTokensParticle(
            amount,
            UInt256.ONE,
            address,
            nonce,
            tokenReference,
            planck,
            ImmutableMap.of(
                    TokenDefinitionParticle.TokenTransition.MINT, TokenPermission.ALL,
                    TokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL
            )
        );

        return new ProcessedOutput(owner, particle);
    }


    public static byte[] toByteArray(ProcessedOutput value) throws IOException {
        DataOutput2 out = new DataOutput2();
        serializer.serialize(out, value);
        return out.copyBytes();
    }

    public static ProcessedOutput fromByteArray(byte[] data) throws IOException {
        DataInput2 input = new DataInput2.ByteArray(data);
        return serializer.deserialize(input, 0);
    }
}
