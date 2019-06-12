package org.radixdlt.millionaire;

import java.io.File;
import java.io.FileInputStream;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.Bootstrap;
import com.radixdlt.client.core.RadixUniverse;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.address.RadixUniverseConfigs;
import com.radixdlt.client.core.network.RadixNode;

import io.reactivex.Observable;

public class Main {
    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.out.println("Please provide at least 1 argument, " +
                    "first pointing to a directory containing .blk files, " +
                    "second pointing to working directory");
        }


        String universeFile = null;
        String blocksDir = args[0];
        String workDir = System.getProperty("user.dir");
        String atomsFile = System.getProperty("user.dir") + "/atoms";
        boolean skipBlocks = false;
        boolean resetAtoms = false;
        boolean rebuildBlocks = false;

        if (args.length > 1) {
            workDir = args[1];

            for(int i=2; i<args.length; i++) {
                switch(args[i]) {
            		case "--atoms":
            			i++;
            			atomsFile = args[i];
            			break;
            		case "--universe":
                		i++;
                		universeFile = args[i];
                		break;
                    case "--skip-blocks":
                        skipBlocks = true;
                        break;
                    case "--reset-atoms":
                        resetAtoms = true;
                        break;
                    case "--rebuild-blocks":
                        rebuildBlocks = true;
                        break;
                    default:
                        // code block
                }
            }
        }

        RadixUniverse universe;
        
        if (universeFile != null)
        {
        	try (FileInputStream fis = new FileInputStream(new File(universeFile)))
        	{
        		RadixUniverseConfig universeConfig = RadixUniverseConfig.fromInputStream(fis);
        		universe = RadixUniverse.create(universeConfig, Observable.just(new RadixNode("localhost", false, 8080)), ImmutableSet.of());
        	}
        }
        else
        	universe = RadixUniverse.create(Bootstrap.LOCALHOST);
        
        BlockchainParser parser = new BlockchainParser(universe, blocksDir, workDir, atomsFile, skipBlocks, resetAtoms, rebuildBlocks);
        parser.parse();
    }
}
