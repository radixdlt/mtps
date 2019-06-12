package org.radixdlt.millionaire;

import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;
import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.client.Serialize;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

public class ProcessedOutput {
    public ECKeyPair owner;
    public TransferrableTokensParticle particle;

    ProcessedOutput(ECKeyPair owner, TransferrableTokensParticle particle) {
        this.owner = owner;
        this.particle = particle;
    }
}


