package org.radixdlt.millionaire;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.RRI;
import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.MessageSerializer;
import org.bitcoinj.core.NetworkParameters;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.client.Serialize;

import java.io.IOException;
import java.io.Serializable;

public class BlockSerializer implements Serializer<Block>, Serializable {

    private MessageSerializer serializer;

    private static BlockSerializer instance;

    BlockSerializer(NetworkParameters networkParameters) {
        this.serializer = networkParameters.getDefaultSerializer();
    }

    public static void initStatic(NetworkParameters networkParameters) {
        instance = new BlockSerializer(networkParameters);
    }


    @Override
    public void serialize(@NotNull DataOutput2 out, @NotNull Block value) throws IOException {
        byte[] serialized = value.bitcoinSerialize();

        out.writeInt(serialized.length);
        out.write(serialized);
    }

    @Override
    public Block deserialize(@NotNull DataInput2 input, int available) throws IOException {
        int length = input.readInt();
        byte[] serialized = new byte[length];
        input.readFully(serialized, 0, length);

        return serializer.makeBlock(serialized);
    }

    public static byte[] toByteArray(Block value) throws IOException {
        DataOutput2 out = new DataOutput2();
        instance.serialize(out, value);
        return out.copyBytes();
    }

    public static Block fromByteArray(byte[] data) throws IOException {
        DataInput2 input = new DataInput2.ByteArray(data);
        return instance.deserialize(input, 0);
    }
}

