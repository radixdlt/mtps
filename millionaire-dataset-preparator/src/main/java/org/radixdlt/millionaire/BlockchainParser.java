package org.radixdlt.millionaire;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.tokens.TokenUnitConversions;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.crypto.ECKeyPair;
import com.radixdlt.client.core.crypto.ECKeyPairGenerator;
import com.radixdlt.client.core.crypto.RadixECKeyPairs;
import com.sleepycat.je.CacheMode;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import org.bitcoin.NativeSecp256k1Util;
import org.bitcoin.Secp256k1Context;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.BlockFileLoader;
import org.radix.crypto.Hash;
import org.radix.utils.UInt256;
import org.radix.utils.primitives.Longs;
import org.radixdlt.millionaire.AtomFileWriter.AtomFileItem;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BlockchainParser {

    // Pubkey lengths and types
    private static final int  PUBKEY_UNCOMPRESSED_LEN  = 65;
    private static final int  PUBKEY_COMPRESSED_LEN    = 33;
    private static final byte PUBKEY_COMPRESSED_Y_EVEN = 0x02;
    private static final byte PUBKEY_COMPRESSED_Y_ODD  = 0x03;

    NetworkParameters np;
    Context context;
    BlockFileLoader blockFileLoader;

    RadixUniverse universe;
    RRI tokenReference;

    Sha256Hash genesisBlockHash = Sha256Hash.wrap("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");

    Environment environment;
    Database blocksDatabase, nextBlockHashDatabase, appProgressDatabase, processedOutputsDatabase;
    Database bannedOutputsDatabase, ignoredOutputsDatabase, uniqueAddressesDatabase, blocksProgressDatabase;

//    ConcurrentMap blocks;
//    ConcurrentMap nextBlockHash;
//    ConcurrentMap<String, byte[]> processedOutputs;
//    ConcurrentMap appProgress;
//    Set bannedOutputs;
//    Set ignoredOutputs;
//    Set uniqueAddresses;

    // Control
    File stopFile;

    // Atoms
    AtomFileWriter atomFileWriter;

    // Stats
    long startTime;
    long totalTransactions;

    PrintWriter statsFile;
    AtomicLong blockNum= new AtomicLong(0);
    AtomicLong validTransactions=new AtomicLong(0);
    AtomicLong bannedTransactions=new AtomicLong(0);
    AtomicLong totalInputs=new AtomicLong(0);
    AtomicLong totalOutputs = new AtomicLong(0);
//    AtomicLong totalOutputLifetime = new AtomicLong(0);
    AtomicLong generatedAddresses = new AtomicLong(0);

    PrintWriter bannedStatsFile;
    AtomicLong bannedBadInput = new AtomicLong(0);
    AtomicLong bannedZeroValue = new AtomicLong(0);
    AtomicLong bannedBadPk = new AtomicLong(0);
    AtomicLong bannedScriptP2SH = new AtomicLong(0);
    AtomicLong bannedScriptP2WSH = new AtomicLong(0);
    AtomicLong bannedScriptP2WPKH = new AtomicLong(0);
    AtomicLong bannedOther = new AtomicLong(0);
    private ECKeyPairGenerator keyPairGenerator;
    private RadixECKeyPairs radixECKeyPairs;
    private Random random = new Random();
    private String blocksDir;
    private boolean skipBlocks;
    private boolean resetAtoms;
    private boolean rebuildBlocks;

    private KeyHandler keyHandler;

	private class CheckpointerTask implements Runnable
	{
		boolean stop = false;
		
		@Override
		public void run()
		{
			CheckpointConfig checkpointConfig = new CheckpointConfig();
			checkpointConfig.setForce(true);

            // STOP
            while (stop == false || stopFile.exists() == false) 
            {
				try
				{
					long start = System.currentTimeMillis();

					while ((stop == false || stopFile.exists() == false) && BlockchainParser.this.environment.cleanLogFile() == true && System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(1))
					{
						Thread.sleep(TimeUnit.SECONDS.toMillis(10));
					}

					if (stop == false || stopFile.exists() == false)
					{
						BlockchainParser.this.environment.checkpoint(checkpointConfig);
						BlockchainParser.this.environment.evictMemory();

						if (System.currentTimeMillis() - start < TimeUnit.MINUTES.toMillis(1))
							Thread.sleep(TimeUnit.MINUTES.toMillis(1) - (System.currentTimeMillis() - start));
					}
				}
				catch (Exception ex)
				{
					System.out.println("Checkpointing of environment failed!");
					ex.printStackTrace();
				}
			}
		}
	}
	private transient Thread 					checkpointThread = null;
	private transient CheckpointerTask			checkpointer = null;

    public BlockchainParser(RadixUniverse universe, String blocksDir, String workDir, String atomsFile, boolean skipBlocks, boolean resetAtoms, boolean rebuildBlocks) throws BlockStoreException, IOException {
        this.blocksDir = blocksDir;
        this.skipBlocks = skipBlocks;
        this.resetAtoms = resetAtoms;
        this.rebuildBlocks = rebuildBlocks;

        // Set up BitcoinJ
        this.np = new MainNetParams();
        this.context = new Context(np);
        this.random.setSeed(System.nanoTime());

        if(Secp256k1Context.isEnabled()) {
            try {
            	this.keyHandler = new Libsecp256k1KeyHandler(random);
                System.out.println("Using Libsecp256k1");
            } catch (NativeSecp256k1Util.AssertFailException e) {
               e.printStackTrace();
            }
        } else {
        	this.keyHandler = new BouncyCastleKeyHandler(new SecureRandom());
            System.out.println("Falling back to bouncy castle");
        }


        // Set up Radix
//        universe = RadixUniverse.create(Bootstrap.LOCALHOST);
        this.universe = universe;
        this.keyPairGenerator = ECKeyPairGenerator.newInstance();
        this.radixECKeyPairs = RadixECKeyPairs.newInstance();

//        ECKeyPair keyPair = addressToKeyPair(Address.fromString(np, "1AGNa15ZQXAZUgFiqJ2i7Z2DPU2J6hW62i"));
//        RadixAddress address = new RadixAddress(universe.getConfig(), keyPair.getPublicKey());
//        System.out.println(address.toString());
//        if(true) throw null;


        // Create token
        createToken();

        // Serialization
        ProcessedOutputSerializer.initStatic(tokenReference);

		System.setProperty("je.disable.java.adler32", "true");

		EnvironmentConfig environmentConfig = new EnvironmentConfig();
		environmentConfig.setTransactional(true);
		environmentConfig.setAllowCreate(true);
		environmentConfig.setLockTimeout(60*10, TimeUnit.SECONDS);
		environmentConfig.setDurability(Durability.COMMIT_NO_SYNC);
//		environmentConfig.setConfigParam(EnvironmentConfig.ENV_DUP_CONVERT_PRELOAD_ALL, "false");
		environmentConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, "100000000");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CHECKPOINTER, "false");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_CLEANER, "false");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_EVICTOR, "false");
		environmentConfig.setConfigParam(EnvironmentConfig.ENV_RUN_VERIFIER, "false");
//		environmentConfig.setConfigParam(EnvironmentConfig.NODE_MAX_ENTRIES, "256");
		environmentConfig.setConfigParam(EnvironmentConfig.TREE_MAX_EMBEDDED_LN, "0");
		environmentConfig.setCacheSize((long)(Runtime.getRuntime().maxMemory()*0.5));
		environmentConfig.setCacheMode(CacheMode.EVICT_LN);

        File dbDir = new File(workDir + "/berkeley");
        if(!dbDir.exists()) {
            dbDir.mkdir();
        }
        this.environment = new Environment(dbDir, environmentConfig);


        if (rebuildBlocks)
        {
            System.out.println("Clearing block databases...");
            try {
                this.environment.truncateDatabase(null, "blocks", false);
                this.environment.truncateDatabase(null, "next_block_hash", false);
                this.environment.truncateDatabase(null, "blocks_progress", false);
            } catch (DatabaseNotFoundException e) {
                // All good
            }
            System.out.println("Done");
        }

		
        System.out.println("Opening block database");
		DatabaseConfig blocksDatabaseConfig = new DatabaseConfig();
		blocksDatabaseConfig.setAllowCreate(true);
		blocksDatabaseConfig.setTransactional(true);
		this.blocksDatabase = this.environment.openDatabase(null, "blocks", blocksDatabaseConfig);

		DatabaseConfig nextBlockHashDatabaseConfig = new DatabaseConfig();
		nextBlockHashDatabaseConfig.setAllowCreate(true);
		nextBlockHashDatabaseConfig.setTransactional(true);
		this.nextBlockHashDatabase = this.environment.openDatabase(null, "next_block_hash", nextBlockHashDatabaseConfig);


        DatabaseConfig blocksProgressDatabaseConfig = new DatabaseConfig();
        blocksProgressDatabaseConfig.setAllowCreate(true);
        blocksProgressDatabaseConfig.setTransactional(true);
        this.blocksProgressDatabase  = this.environment.openDatabase(null, "blocks_progress", blocksProgressDatabaseConfig);


/*		// Blockchain
        blocks = blockDb.hashMap("blocks")
                .keySerializer(Serializer.STRING_ASCII)
//                .valueSerializer(Serializer.BYTE_ARRAY)
                .valueSerializer(blockSerializer)
                .counterEnable()
                .createOrOpen();
        nextBlockHash = blockDb.treeMap("nextBlockHash")
                .keySerializer(Serializer.STRING_ASCII)
                .valueSerializer(Serializer.STRING_ASCII)
                .createOrOpen();
        //        nextBlockHash = new ConcurrentHashMap();*/



        if (resetAtoms)
		{
            System.out.println("Clearing work databases...");
		    try {
                this.environment.truncateDatabase(null, "processed_outputs", false);
                this.environment.truncateDatabase(null, "app_progress", false);
                this.environment.truncateDatabase(null, "banned_outputs", false);
                this.environment.truncateDatabase(null, "ignored_outputs", false);
                this.environment.truncateDatabase(null, "unique_addresses", false);
            } catch (DatabaseNotFoundException e) {
		        // All good
            }
            System.out.println("Done");
		}

        System.out.println("Opening work databases");

        DatabaseConfig processedOutputsDatabase = new DatabaseConfig();
		processedOutputsDatabase.setAllowCreate(true);
		processedOutputsDatabase.setTransactional(true);
		this.processedOutputsDatabase = this.environment.openDatabase(null, "processed_outputs", processedOutputsDatabase);

		DatabaseConfig appProgressDatabaseConfig = new DatabaseConfig();
		appProgressDatabaseConfig.setAllowCreate(true);
		appProgressDatabaseConfig.setTransactional(true);
		this.appProgressDatabase  = this.environment.openDatabase(null, "app_progress", appProgressDatabaseConfig);

		DatabaseConfig bannedOutputsDatabaseConfig = new DatabaseConfig();
		bannedOutputsDatabaseConfig.setAllowCreate(true);
		bannedOutputsDatabaseConfig.setTransactional(true);
		this.bannedOutputsDatabase  = this.environment.openDatabase(null, "banned_outputs", bannedOutputsDatabaseConfig);
        
		DatabaseConfig ignoredOutputsDatabaseConfig = new DatabaseConfig();
		ignoredOutputsDatabaseConfig.setAllowCreate(true);
		ignoredOutputsDatabaseConfig.setTransactional(true);
		this.ignoredOutputsDatabase = this.environment.openDatabase(null, "ignored_outputs", ignoredOutputsDatabaseConfig);
        
		DatabaseConfig uniqueAddressesDatabaseConfig = new DatabaseConfig();
		uniqueAddressesDatabaseConfig.setAllowCreate(true);
		uniqueAddressesDatabaseConfig.setTransactional(true);
		this.uniqueAddressesDatabase  = this.environment.openDatabase(null, "unique_addresses", uniqueAddressesDatabaseConfig);

		if (resetAtoms)
		{
			new File(atomsFile).delete();
			new File(workDir + "/stats.csv").delete();
			new File(workDir + "/banned_stats.csv").delete();
		}
			
		atomFileWriter = new AtomFileWriter(new File(atomsFile));

        statsFile = new PrintWriter(workDir + "/stats.csv");
        statsFile.println("block,validTx,bannedTx,totalInputs,totalOutputs,unusedOutputs,uniqueAddresses,generatedAddresses");

        bannedStatsFile = new PrintWriter(workDir + "/banned_stats.csv");
        bannedStatsFile.println("block,badInput,zeroValue,badPk,p2sh,p2wsh,p2wpkh,other");

        stopFile = new File(workDir + "/STOP");
        
		this.checkpointThread = new Thread (this.checkpointer = new CheckpointerTask());
		this.checkpointThread.setDaemon(false);
		this.checkpointThread.setName("Checkpointer");
		this.checkpointThread.start();
    }

    private void createToken() {
        tokenReference = universe.getNativeToken();
    }

    private long loadProgressValue(String key, long defaultValue) {
        DatabaseEntry dbKey = new DatabaseEntry(key.getBytes());
        DatabaseEntry dbValue = new DatabaseEntry();
        if (OperationStatus.NOTFOUND == this.blocksProgressDatabase.get(null, dbKey, dbValue, LockMode.DEFAULT)) {
            return defaultValue;
        }
        return Longs.fromByteArray(dbValue.getData());
    }

    private void storeProgressValue(String key, long value) {
        DatabaseEntry dbKey = new DatabaseEntry(key.getBytes());
        this.blocksProgressDatabase.put(null, dbKey, new DatabaseEntry(Longs.toByteArray(value)));
    }


    private List<File> getBlockFileList() {
        // Load last incomplete block file from db
        long lastCompleteBlockFile = loadProgressValue("lastCompleteBlockFile", 0);
        System.out.println(String.format(Locale.US, "Loading blocks starting from blk%05d.dat", lastCompleteBlockFile));

        List<File> list = new LinkedList<>();
        for (long i = lastCompleteBlockFile; true; i++) {
            File file = new File(blocksDir, String.format(Locale.US, "blk%05d.dat", i));
            if (!file.exists()) {
                // Store last complete block file in db
                storeProgressValue("lastCompleteBlockFile", Math.max(i - 2, 0));
                break;
            }
            list.add(file);
        }

        return list;
    }


    private void computeBlockChain() throws IOException {
        System.out.println("Reading blocks from blk*.dat");

        blockFileLoader = new BlockFileLoader(np, getBlockFileList());

        long blockCount = loadProgressValue("storedBlockCount", 0);
        totalTransactions = loadProgressValue("totalTransactions", 0);

        // Iterate over the blocks in the dataset.
        for (Block block : blockFileLoader) {
            DatabaseEntry currBlockHash = new DatabaseEntry(block.getHash().getBytes());
            DatabaseEntry prevBlockHash = new DatabaseEntry(block.getPrevBlockHash().getBytes());
            
            com.sleepycat.je.Transaction dbtx = this.environment.beginTransaction(null, null);
            
            try
            {
                if (OperationStatus.NOTFOUND == this.blocksDatabase.get(dbtx, currBlockHash, new DatabaseEntry(), LockMode.DEFAULT)) {
                    if(blockCount % 100 == 0)
                        System.out.println("Loaded block "+blockCount);

                    blockCount++;

                    DatabaseEntry blockData = new DatabaseEntry(block.bitcoinSerialize());

                    this.blocksDatabase.put(dbtx, currBlockHash, blockData);
                    this.nextBlockHashDatabase.put(dbtx, prevBlockHash, currBlockHash);

                    totalTransactions += block.getTransactions().size();
                }
            }
            finally
            {
            	dbtx.commit();
            }
        }

        storeProgressValue("totalTransactions", totalTransactions);
        storeProgressValue("storedBlockCount", blockCount);


        System.out.println("Chain built, total blocks loaded: "+blockCount);
    }



    public void parse() throws BlockStoreException, IOException, InterruptedException {

        if(!skipBlocks) {
            computeBlockChain();
        }
        else
        	totalTransactions = loadProgressValue("totalTransactions", 408_000_000);

        System.out.println("Starting to parse blocks");
        System.out.println("Create \'STOP\' file in data directory to safely stop");

        long totalBlocks = this.blocksDatabase.count();

        System.out.println("Total blocks: "+totalBlocks);

        long lastBlockTime = 0;
        long lastTransactions = 0;
        Sha256Hash currentBlockHash;

        DatabaseEntry blockNumKey = new DatabaseEntry("blockNum".getBytes());
        DatabaseEntry validTransactionsKey = new DatabaseEntry("validTransactions".getBytes());
        DatabaseEntry bannedTransactionsKey = new DatabaseEntry("bannedTransactions".getBytes());
        DatabaseEntry totalOutputsKey = new DatabaseEntry("totalOutputs".getBytes());
        DatabaseEntry totalInputsKey = new DatabaseEntry("totalInputs".getBytes());

    	DatabaseEntry lastBlockHashKey = new DatabaseEntry("lastBlockHash".getBytes());
    	DatabaseEntry lastBlockHashData = new DatabaseEntry();
        DatabaseEntry currBlockHashKey = new DatabaseEntry();
    	DatabaseEntry currBlockHashData = new DatabaseEntry();
    	
        if (OperationStatus.SUCCESS != this.appProgressDatabase.get(null, lastBlockHashKey, lastBlockHashData, LockMode.DEFAULT))
            currentBlockHash = genesisBlockHash;
        else
        {
            if (OperationStatus.SUCCESS != this.nextBlockHashDatabase.get(null, lastBlockHashData, currBlockHashData, LockMode.DEFAULT)) {
                System.out.println("Already up to date");
                return;
            }

            currentBlockHash = Sha256Hash.wrap(currBlockHashData.getData());

            DatabaseEntry blockNumData = new DatabaseEntry();
            DatabaseEntry validTransactionsData = new DatabaseEntry();
            DatabaseEntry bannedTransactionsData = new DatabaseEntry();
            DatabaseEntry totalOutputsData = new DatabaseEntry();
            DatabaseEntry totalInputsData = new DatabaseEntry();
            
            this.appProgressDatabase.get(null, blockNumKey, blockNumData, LockMode.DEFAULT);
            this.appProgressDatabase.get(null, validTransactionsKey, validTransactionsData, LockMode.DEFAULT);
            this.appProgressDatabase.get(null, bannedTransactionsKey, bannedTransactionsData, LockMode.DEFAULT);
            this.appProgressDatabase.get(null, totalOutputsKey, totalOutputsData, LockMode.DEFAULT);
            this.appProgressDatabase.get(null, totalInputsKey, totalInputsData, LockMode.DEFAULT);
            
            blockNum.set(Longs.fromByteArray(blockNumData.getData()));
            validTransactions.set(Longs.fromByteArray(validTransactionsData.getData()));
            bannedTransactions.set(Longs.fromByteArray(bannedTransactionsData.getData()));
            totalOutputs.set(Longs.fromByteArray(totalOutputsData.getData()));
            totalInputs.set(Longs.fromByteArray(totalInputsData.getData()));
        }

        startTime = System.currentTimeMillis();
        
        ExecutorService executors = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        // Iterate over the blocks in the dataset.
        while(currentBlockHash != null) {
            com.sleepycat.je.Transaction dbtx = this.environment.beginTransaction(null, null);
            
            try
            {
	            // Store progress
                currBlockHashKey = new DatabaseEntry(currentBlockHash.getBytes());
	            this.appProgressDatabase.put(dbtx, lastBlockHashKey, currBlockHashKey);
	
	            // Get block
	            DatabaseEntry blockData = new DatabaseEntry();
	            DatabaseEntry nextBlockHashData = new DatabaseEntry();
	            if (OperationStatus.NOTFOUND == this.blocksDatabase.get(dbtx, currBlockHashKey, blockData, LockMode.DEFAULT))
	                throw new Error("Block "+currentBlockHash+" not found");
	            
	            Block block = new Block(this.np, blockData.getData());
	            
	            if (OperationStatus.NOTFOUND == this.nextBlockHashDatabase.get(dbtx, currBlockHashKey, nextBlockHashData, LockMode.DEFAULT)) {
                    System.out.println("Next block not found");
                    currentBlockHash = null;
                } else
	                currentBlockHash = Sha256Hash.wrap(nextBlockHashData.getData());
	            
	            blockNum.incrementAndGet();

	            List<Transaction> transactions = block.getTransactions();
	            final long blockTime = block.getTime().getTime();
	            final Deque<Transaction> transactionsDeque = new ArrayDeque<Transaction>(transactions);
	            final Deque<AtomFileItem> atomsDeque = new ArrayDeque<AtomFileItem>(transactions.size());
	            final Map<Script, AbstractMap.SimpleEntry<ECKeyPair, RadixAddress>> outputKeys = new ConcurrentHashMap<Script, AbstractMap.SimpleEntry<ECKeyPair, RadixAddress>>();
	            final CountDownLatch txLatch = new CountDownLatch(transactions.size());

	            Runnable keyGeneratorRunnable = new Runnable()
           		{
					@Override
					public void run()
					{
						while(txLatch.getCount() > 0)
						{
							Transaction transaction;
							
							synchronized(transactionsDeque)
							{
								transaction = transactionsDeque.pop();
							}
							
							if (transaction == null)
								break;
							
							try
							{
				                if(!isTransactionValid(dbtx, transaction))
				                	continue;
				                
				                List<TransactionOutput> outputs = transaction.getOutputs();
				                for(TransactionOutput output : outputs) 
				                {
					                // Address
					                ECKeyPair key;
					                RadixAddress address;
					                try {
					                    Script script = output.getScriptPubKey();
					                    
					                    if (outputKeys.containsKey(script) == false)
					                    {
					                    	key = addressToKeyPair(script.getToAddress(np, true));
					                        address = new RadixAddress(universe.getConfig(), key.getPublicKey());
					                    	outputKeys.put(script, new AbstractMap.SimpleEntry<ECKeyPair, RadixAddress>(key, address));
					                    }
					                } catch(Exception e) {}
				                }
							}
							finally
							{
								txLatch.countDown();
							}
						}							
					}
           		};
           		
                for (int e = 0 ; e < Runtime.getRuntime().availableProcessors() ; e++)
                    executors.submit(keyGeneratorRunnable);
           		
           		txLatch.await();
	            
	            for(Transaction tx : transactions) 
	            {
	            	
	                // if the transaction is valid
	                if(!isTransactionValid(dbtx, tx)) {
	                    banAllOutputs(dbtx, tx);
	                    bannedTransactions.incrementAndGet();
	                    continue;
	                }
	
	                validTransactions.incrementAndGet();
	
	                Set<ECKeyPair> signers = new HashSet<>();
	                List<SpunParticle<TransferrableTokensParticle>> particles = new ArrayList<>();
	
	                // Could be threaded
	                boolean requireOutputSignature = false;
	                List<TransactionInput> inputs = tx.getInputs();
	                for(TransactionInput input : inputs) {
	                    if(input.isCoinBase()) {
	                        // Don't need to add any particles in the current model
	                        requireOutputSignature = true;
	                    } else if (!isIgnored(dbtx, input)) {
	                        totalInputs.incrementAndGet();
	
	                        ProcessedOutput po = getInput(dbtx, input);
	                        particles.add(SpunParticle.down(po.particle));
	
	//                        totalOutputLifetime += validTransactions - po.addedTxNumber;
	
	                        // Add owner to signer
	                        signers.add(po.owner);
	                    }
	                }
	
	
	                // Build atom
	                // Could be threaded
	                List<TransactionOutput> outputs = tx.getOutputs();
	                for(TransactionOutput output : outputs) {
	                    if (output.getValue().isZero()) {
	                        ignoreOutput(dbtx, output);
	                        bannedZeroValue.incrementAndGet();
	                    } else {
	                        ProcessedOutput po = getOutputParticle(dbtx, output, validTransactions.get(), outputKeys);
	                        totalOutputs.incrementAndGet();
	                        particles.add(SpunParticle.up(po.particle));
	
	                        if(requireOutputSignature) {
	                            signers.add(po.owner);
	                            requireOutputSignature = false;
	                        }
	                    }
	                }
	
	                if(requireOutputSignature) {
	                    System.out.println("WARN: couldn\'t get output signature "+tx.getTxId().toString());
	                }
	
	                if(signers.size() == 0) {
	                    System.out.println("WARN: transaction with no signatures "+tx.getTxId().toString());
	                }
	
	                synchronized(atomsDeque)
	                {
	                	atomsDeque.push(new AtomFileWriter.AtomFileItem(particles, signers, tx, universe, keyHandler, blockTime));
	                }
	            }
	            
	            CountDownLatch atomsLatch = new CountDownLatch(atomsDeque.size());
	            Runnable atomsBuilderRunnable = new Runnable()
           		{
					@Override
					public void run()
					{
						while(atomsLatch.getCount() > 0)
						{
							AtomFileItem atomFileItem;
							
							synchronized(atomsDeque)
							{
								atomFileItem = atomsDeque.pop();
							}
							
							if (atomFileItem == null)
								break;
							
							try
							{
								atomFileItem.buildAtomRecord();

				                synchronized(atomFileWriter)
				                {
				                	atomFileWriter.push(atomFileItem);
				                }
							}
							finally
							{
								atomsLatch.countDown();
							}
						}							
					}
           		};

                for (int e = 0 ; e < Runtime.getRuntime().availableProcessors() ; e++)
                    executors.submit(atomsBuilderRunnable);

                atomsLatch.await();
	
	            this.appProgressDatabase.put(dbtx, blockNumKey, new DatabaseEntry(Longs.toByteArray(blockNum.get())));
	            this.appProgressDatabase.put(dbtx, validTransactionsKey, new DatabaseEntry(Longs.toByteArray(validTransactions.get())));
	            this.appProgressDatabase.put(dbtx, bannedTransactionsKey, new DatabaseEntry(Longs.toByteArray(bannedTransactions.get())));
	            this.appProgressDatabase.put(dbtx, totalOutputsKey, new DatabaseEntry(Longs.toByteArray(totalOutputs.get())));
	            this.appProgressDatabase.put(dbtx, totalInputsKey, new DatabaseEntry(Longs.toByteArray(totalInputs.get())));
	
	            if(blockNum.get() % 1000 == 0) {
	
	                statsFile.printf("%d,%d,%d,%d,%d,%d,%d,%d%n",
	                        blockNum.get(),
	                        validTransactions.get(),
	                        bannedTransactions.get(),
	                        totalInputs.get(),
	                        totalOutputs.get(),
	                        0, //processedOutputs.size(),
	                        0, //uniqueAddresses.size(),
	                        generatedAddresses.get());
	                statsFile.flush();
	
	                bannedStatsFile.printf("%d,%d,%d,%d,%d,%d,%d,%d%n",
	                        blockNum.get(),
	                        bannedBadInput.get(),
	                        bannedZeroValue.get(),
	                        bannedBadPk.get(),
	                        bannedScriptP2SH.get(),
	                        bannedScriptP2WSH.get(),
	                        bannedScriptP2WPKH.get(),
	                        bannedOther.get());
	                bannedStatsFile.flush();
	
	
	                long transactionsProcessed = validTransactions.get() + bannedTransactions.get();
	                Double tps = (transactionsProcessed - lastTransactions) / ((System.currentTimeMillis()-lastBlockTime) / 1000.0);
	
	                long secondsLeft = new Double((totalTransactions - transactionsProcessed) / tps).longValue();
	                long minutesLeft = secondsLeft / 60;
	                long hoursLeft = minutesLeft / 60;
	
	                System.out.printf("Block %d/%d%n", blockNum.get(), totalBlocks);
	                System.out.printf("Transaction %d/%d%n", transactionsProcessed, totalTransactions);
	                System.out.printf("%.2f tps%n", tps);
	                System.out.printf("%.2f%%, ", transactionsProcessed*100.0/totalTransactions);
	                System.out.printf("%d:%02d:%02d left%n", hoursLeft, minutesLeft%60, secondsLeft%60);
                    System.out.printf("Atom write queue length: %d%n", atomFileWriter.writeQueue.size());

                    lastBlockTime = System.currentTimeMillis();
	                lastTransactions = transactionsProcessed;
	            }

	            dbtx.commit();
            }
            catch (Throwable t)
            {
            	dbtx.abort();
            	throw t;
            }

            // STOP
            if (stopFile.exists()) {
                break;
            }
        }

        System.out.println("Last block: "+Sha256Hash.wrap(currBlockHashKey.getData()));
        System.out.println("Total time: "+(System.currentTimeMillis() - startTime)+"ms");
        System.out.println("Closing all files...");

        this.checkpointer.stop = true;
        this.appProgressDatabase.close();
        this.bannedOutputsDatabase.close();
        this.blocksDatabase.close();
        this.blocksProgressDatabase.close();
        this.nextBlockHashDatabase.close();
        this.processedOutputsDatabase.close();
        this.uniqueAddressesDatabase.close();
        this.ignoredOutputsDatabase.close();
        this.environment.close();

        statsFile.close();
        bannedStatsFile.close();

        atomFileWriter.completed();
        atomFileWriter.waitToFinish(1000L);

        System.out.println("DONE");
    }

    private boolean isIgnored(com.sleepycat.je.Transaction dbtx, TransactionInput input) {
        return this.ignoredOutputsDatabase.get(dbtx, new DatabaseEntry(getOutputUniqueId(input).getBytes()), null, LockMode.DEFAULT) == OperationStatus.SUCCESS ? true : false;
    }

    private void ignoreOutput(com.sleepycat.je.Transaction dbtx, TransactionOutput output) {
        this.ignoredOutputsDatabase.put(dbtx, new DatabaseEntry(getOutputUniqueId(output).getBytes()), new DatabaseEntry(getOutputUniqueId(output).getBytes()));
    }

    private ProcessedOutput getInput(com.sleepycat.je.Transaction dbtx, TransactionInput input) throws IOException {
        String btcOutputId = getOutputUniqueId(input);
        DatabaseEntry btcOutputIdKey = new DatabaseEntry(btcOutputId.getBytes());
        DatabaseEntry processedOutputData = new DatabaseEntry();

        if (OperationStatus.SUCCESS != this.processedOutputsDatabase.get(dbtx, btcOutputIdKey, processedOutputData, LockMode.DEFAULT))
            throw new Error("Couldn't find output for input");


        ProcessedOutput po = ProcessedOutputSerializer.fromByteArray(processedOutputData.getData());
        this.processedOutputsDatabase.delete(dbtx, btcOutputIdKey);

        return po;
    }

    private ProcessedOutput getOutputParticle(com.sleepycat.je.Transaction dbtx, TransactionOutput output, long txNumber, Map<Script, AbstractMap.SimpleEntry<ECKeyPair, RadixAddress>> keys) throws IOException {
        // Compute amount
        UInt256 rawAmount = TokenUnitConversions.unitsToSubunits(new BigDecimal(output.getValue().toPlainString()));
        //granularity
        UInt256 granularity = UInt256.ONE;


        // Address
        AbstractMap.SimpleEntry<ECKeyPair, RadixAddress> keyEntry;
        ECKeyPair key;
        RadixAddress address;
        
        try {
            Script script = output.getScriptPubKey();
            
            keyEntry = keys.get(script);
            if (keyEntry == null)
            {
            	key = addressToKeyPair(script.getToAddress(np, true));
                address = new RadixAddress(universe.getConfig(), key.getPublicKey());
            }
            else
            {
            	key = keyEntry.getKey();
            	address = keyEntry.getValue();
            }
        } catch(Exception e) {
            key = randomKeyPair();
            address = new RadixAddress(universe.getConfig(), key.getPublicKey());
            generatedAddresses.incrementAndGet();
        }

        // uniqueAddresses.add(keyPair.getPrivateKey());

        // TODO: use btc time?
        long nonce = System.nanoTime();
        long planck = System.currentTimeMillis() / 60000L + 60000L;

        TransferrableTokensParticle particle = new TransferrableTokensParticle(
            rawAmount,
            granularity,
            address,
            nonce,
            tokenReference,
            planck,
            ImmutableMap.of(
                    TokenDefinitionParticle.TokenTransition.MINT, TokenPermission.ALL,
                    TokenDefinitionParticle.TokenTransition.BURN, TokenPermission.ALL
            )
        );

        String btcOutputId = getOutputUniqueId(output);

        ProcessedOutput processedOutput = new ProcessedOutput(key, particle);
//        this.processedOutputs.put(btcOutputId, processedOutput);
        this.processedOutputsDatabase.put(dbtx, new DatabaseEntry(btcOutputId.getBytes()), new DatabaseEntry(ProcessedOutputSerializer.toByteArray(processedOutput)));

        return processedOutput;
    }


    private ECKeyPair addressToKeyPair(Address address){
        byte[] pk = Hash.sha256(address.getHash());
//        byte[] pk = address.getHash();
        return pkToKeyPair(pk);
//        return radixECKeyPairs.generateKeyPairFromSeed(address.getHash());
    }

    private ECKeyPair pkToKeyPair(byte[] pk) {
        byte[] pubKey = new byte[0];
        try {
            pubKey = keyHandler.computePublicKey(pk);
        } catch (CryptoException e) {
            e.printStackTrace();
        }

        return new ECKeyPair(pubKey, pk);

//        ECKey pair = ECKey.fromPrivate(pk);
//        return new ECKeyPair(pair.getPubKey(), pk);
    }


    private ECKeyPair randomKeyPair(){
        byte[] pk = new byte[32];
        random.nextBytes(pk);
        return pkToKeyPair(pk);
    }


    private void banAllOutputs(com.sleepycat.je.Transaction dbtx, Transaction tx) {
        for(TransactionOutput output: tx.getOutputs()) {
            bannedBadInput.incrementAndGet();
            banOutput(dbtx, output);
        }
    }

    private void banOutput(com.sleepycat.je.Transaction dbtx, TransactionOutput output) {
        this.bannedOutputsDatabase.put(dbtx, new DatabaseEntry(getOutputUniqueId(output).getBytes()), new DatabaseEntry(getOutputUniqueId(output).getBytes()));
    }

    private boolean isTransactionValid(com.sleepycat.je.Transaction dbtx, Transaction tx) {
        // Exclude transactions depending on invalid transactons
        for(TransactionInput input : tx.getInputs()) {

            if (!input.isCoinBase()) {
                String outputUniqueId = getOutputUniqueId(input);

                if(this.bannedOutputsDatabase.get(dbtx, new DatabaseEntry(outputUniqueId.getBytes()), null, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                    return false;
                }
            }
        }

        return true;
    }


    private String getOutputUniqueId(TransactionOutput output) {
        return output.getParentTransaction().getTxId().toString()+output.getIndex();
    }

    private String getOutputUniqueId(TransactionInput input) {
        return input.getOutpoint().getHash().toString()+input.getOutpoint().getIndex();
    }

}
