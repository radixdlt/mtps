package org.radixdlt.millionaire;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.ParticleGroup;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECSignature;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.radix.common.ID.EUID;
import org.radix.utils.UInt256;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public class AtomFileWriter {
    Queue<AtomFileItem> writeQueue;
    Thread writerThread = null;
    boolean completed = false;

    File file;


    AtomFileWriter(File file) {
        this.file = file;
        this.writeQueue = new ConcurrentLinkedQueue<>();
    }

    public void push(AtomFileItem item) {
        this.writeQueue.add(item);
        startIfRequired();
    }

    public void completed() {
        completed = true; //will not stop accepting new requests, only helps with timeout.
    }

    private void startIfRequired() {
        if(!writeQueue.isEmpty() && (writerThread == null || !writerThread.isAlive())) {
            synchronized(this) {
                if(!writeQueue.isEmpty() && (writerThread == null || !writerThread.isAlive())) {
                    writerThread = new Thread(new WriterRunnable(file));
                    writerThread.start();
                }
            }
        }
    }

    public void waitToFinish(long timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        while(!writeQueue.isEmpty() && System.currentTimeMillis() - start < timeout) {
            startIfRequired();
            Thread.sleep(1L);
        }
    }

    class WriterRunnable implements Runnable{
        File f;
        long timeout = 1000L; //don't need to wait too long
        public WriterRunnable(File f) {
            this.f = f;
        }
        public void run(){
            try {
                DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f, true),10*1024*1024));

                long start = System.currentTimeMillis();
                while(true) {
                    if(!writeQueue.isEmpty()) {
                        AtomFileItem item = writeQueue.poll();

                        item.getAtomFileRecord().serializeToStream(writer);

                        writer.flush();
                    }else if(!completed && System.currentTimeMillis() - start < timeout){
                        Thread.sleep(1L);
                    }else {
                        break;
                    }
                }
                writer.flush();
                writer.close();
            } catch (Exception e) {
                Thread t = Thread.currentThread();
                t.getUncaughtExceptionHandler().uncaughtException(t, e);
            }
        }
    }


    public static class AtomFileItem {
        List<SpunParticle<TransferrableTokensParticle>> particles;
        Set<ECKeyPair> signers;
        Transaction btcTransaction;
        RadixUniverse universe;
        private KeyHandler keyHandler;
        private final long blockTime;
        private AtomFileRecord atomFileRecord;

        AtomFileItem(List<SpunParticle<TransferrableTokensParticle>> particles, Set<ECKeyPair> signers, Transaction btcTransaction, RadixUniverse universe, KeyHandler keyHandler, long blockTime) {
            this.particles = particles;
            this.signers = signers;
            this.btcTransaction = btcTransaction;
            this.universe = universe;
            this.keyHandler = keyHandler;
            this.blockTime = blockTime;
        }

        public void buildAtomRecord() 
        {
        	if (this.atomFileRecord != null)
        		return;
        	
            String btcTxId = btcTransaction.getTxId().toString();

            final long blockTimeMillis = blockTime;
            Map<String, String> metaData = new HashMap<>();
            metaData.put("btcTxId", btcTxId);
            metaData.put(Atom.METADATA_TIMESTAMP_KEY, String.valueOf(blockTimeMillis));

            // Create atom
            List<ParticleGroup> pgs = new ArrayList<>();
            pgs.add(ParticleGroup.of(particles.toArray(new SpunParticle[0])));
            Atom atom = new Atom(pgs, metaData);

            // Sign atom by all signers
            Map<String, ECSignature> signatures = atom.getSignatures();
            Map<EUID, ECSignature> signaturesByUid = new HashMap<>();
            byte[] atomHash = atom.getHash().toByteArray();

            for(ECKeyPair signer: signers) {
                try {
                    ECSignature signature = keyHandler.sign(atomHash, signer.getPrivateKey());
                    String id = signer.getUID().toString();
                    signatures.put(id, signature);
                    signaturesByUid.put(signer.getUID(), signature);
                } catch (CryptoException e) {
                    e.printStackTrace();
                }
            }

            // Store to disk
            Set<Long> shards = atom.spunParticles()
                .map(SpunParticle<Particle>::getParticle)
                .map(Particle::getShardables)
                .flatMap(Set::stream)
                .map(RadixAddress::getUID)
                .map(EUID::getShard)
                .collect(Collectors.toSet());

            this.atomFileRecord = new AtomFileRecord(btcTransaction.getTxId(), shards, particles, signaturesByUid, blockTimeMillis);
        }
        
        public AtomFileRecord getAtomFileRecord()
        {
        	if (this.atomFileRecord == null)
        		buildAtomRecord();
        	
        	return this.atomFileRecord;
        }
    }

    public static class AtomFileRecord {

        private final Sha256Hash txId;
        private final Set<Long> shards;
        private final List<SpunParticle<TransferrableTokensParticle>> particles;
        private final Map<EUID, ECSignature> signatures;
        private final long blockTimeMillis;

        public AtomFileRecord(Sha256Hash txId, Set<Long> shards, List<SpunParticle<TransferrableTokensParticle>> particles, Map<EUID, ECSignature> signatures, long blockTimeMillis) {

            this.txId = txId;
            this.shards = shards;
            this.particles = particles;
            this.signatures = signatures;
            this.blockTimeMillis = blockTimeMillis;
        }

        private static ByteArrayOutputStream bufferOut = new ByteArrayOutputStream(65536);
		private static DataOutputStream dataBufferOut = new DataOutputStream(bufferOut);

	    public void serializeToStream(DataOutputStream out) throws IOException {
            // tx id - needs to be put back into metadata after deserialisation!
            byte[] txIdBytes =  this.txId.getBytes();
            out.writeInt(txIdBytes.length);
            out.write(txIdBytes);

            // tx time
            out.writeLong(blockTimeMillis);

            // shards
            out.writeInt(shards.size());
            for(Long shard: shards) {
                out.writeLong(shard);
            }

            bufferOut.reset();
            // particles
		    dataBufferOut.writeInt(particles.size());
            for (SpunParticle<TransferrableTokensParticle> particle : particles) {
                final TransferrableTokensParticle transfer = particle.getParticle();
	            dataBufferOut.writeBoolean(particle.getSpin() == Spin.UP); // spin
	            dataBufferOut.write(transfer.getAddress().getPublicKey().getPublicKey()); // constant length bytes of address without magic
                writeUInt256(transfer.getAmount(), dataBufferOut); // amount as 4 longs
	            dataBufferOut.writeLong(transfer.getNonce()); // nonce as long
	            dataBufferOut.writeLong(transfer.getPlanck()); // planck as long
            }

            // signatures
		    dataBufferOut.writeInt(signatures.size());
            for (Map.Entry<EUID, ECSignature> signatureEntry : signatures.entrySet()) {
                writeEUID(signatureEntry.getKey(), dataBufferOut);
                final byte[] rBytes = signatureEntry.getValue().getR().toByteArray();
	            dataBufferOut.writeInt(rBytes.length);
	            dataBufferOut.write(rBytes);
                final byte[] sBytes = signatureEntry.getValue().getS().toByteArray();
	            dataBufferOut.writeInt(sBytes.length);
	            dataBufferOut.write(sBytes);
            }

            out.writeInt(bufferOut.size());
            bufferOut.writeTo(out);
        }

        private void writeEUID(EUID value, DataOutputStream out) throws IOException {
            out.write(value.toByteArray());
        }

        private void writeUInt256(UInt256 value, DataOutputStream out) throws IOException {
            out.writeLong(value.getHigh().getHigh());
            out.writeLong(value.getHigh().getLow());
            out.writeLong(value.getLow().getHigh());
            out.writeLong(value.getLow().getLow());
        }
    }
}
