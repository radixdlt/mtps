var fs = require('fs');
var http = require('http');
var url = require('url');

const DEFAULT_PORT = 8080
const SEEDS_FILE = "seeds.db";

const SHARD_COUNT = parseInt(process.env.SHARD_COUNT || 100);
const SHARD_OVERLAP = parseFloat(process.env.SHARD_OVERLAP || 0.1);
const MAX_SHARDS_PER_CHUNK = 17592186044416; // 2^44

// number of shards per chunk
const TARGET_SHARDS_PER_CHUNK = Math.trunc(MAX_SHARDS_PER_CHUNK/(SHARD_COUNT+SHARD_OVERLAP));

// overlapping shards 
const OVERLAP_SHARDS = Math.trunc(TARGET_SHARDS_PER_CHUNK * SHARD_OVERLAP);

// adding overlap 
const EXT_TARGET_SHARDS_PER_CHUNK = TARGET_SHARDS_PER_CHUNK + OVERLAP_SHARDS;

// the trange to brute force when allocating the node key
// With smaller range the key will be more exact but it will take longer time to generate
const BF_TARGET_SHARDS_PER_CHUNK = Math.trunc(TARGET_SHARDS_PER_CHUNK * (SHARD_OVERLAP/2));
//17592186044416
//1935140464885
// start listening for requests 
function start() {
    console.log("SHARD_COUNT:     "+SHARD_COUNT)
    console.log("SHARD_OVERLAP:   "+SHARD_OVERLAP)
    console.log("OVERLAP_SHARDS:   "+OVERLAP_SHARDS)
    console.log("MAX_SHARDS_PER_CHUNK:      "+MAX_SHARDS_PER_CHUNK)
    console.log("TARGET_SHARDS_PER_CHUNK:     "+TARGET_SHARDS_PER_CHUNK)
    console.log("EXT_TARGET_SHARDS_PER_CHUNK: "+EXT_TARGET_SHARDS_PER_CHUNK)
    console.log("BF_TARGET_SHARDS_PER_CHUNK:  "+BF_TARGET_SHARDS_PER_CHUNK)
    http.createServer(function (req, res) {
        var path = url.parse(req.url).path;

        // /shard endpoint
        if (path.indexOf("/shard") !== -1) {
            // process request
            processShardRequest(req, res);
        } else {
            res.statusCode = 404;
            res.end();
        }
    }).listen(DEFAULT_PORT, ()=>{console.log(`Server listening on port ${DEFAULT_PORT}`)});
}

// process request for 
function processShardRequest(req, res) {
    var query = url.parse(req.url, true).query;
    var seed = ('seed' in query ? query.seed : -1)

    // invalid seed
    if (seed == -1 || seed == "") {
        res.statusCode = 422;
        res.end();
        return;
    }

    // if the file is removed we start over
    var seeds = loadSeeds()
    var index = Object.keys(seeds).length;

    
    let offset = OVERLAP_SHARDS / 2;
    
    // add to seeds map
    if (!(seed in seeds)) {
        // The targetted anchor point should be in the middle of a chunk
        let i = index % SHARD_COUNT + 0.5;
        let targetAnchor = Math.trunc(offset + TARGET_SHARDS_PER_CHUNK*i - MAX_SHARDS_PER_CHUNK/2);

        console.log("targetAnchor: "+targetAnchor)
        seeds[seed] = `${targetAnchor} ${BF_TARGET_SHARDS_PER_CHUNK} ${EXT_TARGET_SHARDS_PER_CHUNK}`;
        persistSeeds(seeds);
    }

    res.write(`${seeds[seed]}\n`)
    res.end()

}

function persistSeeds(seeds) {
    try {
        fs.writeFileSync(`${SEEDS_FILE}.tmp`, JSON.stringify(seeds), { encoding: 'utf-8' });
        fs.renameSync(`${SEEDS_FILE}.tmp`, SEEDS_FILE);
    } catch (err) {
        console.error(err.message);
    }
}

function loadSeeds() {
    if (fs.existsSync(SEEDS_FILE)) {
        try {
            var json = fs.readFileSync(SEEDS_FILE, { encoding: 'utf-8' })
            return JSON.parse(json)
        } catch (err) {
            console.error(err.message)
        }
    }
    return {}
}

module.exports.start = start

