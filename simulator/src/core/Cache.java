package core;

public class Cache {

    private final int cacheSize;
    private final int blockSize;
    private final int associativity;
    private final int numSets;

    private final CacheLine[][] sets; // [numSets][associativity]

    private final int hitLatency;
    private final int missPenalty;

    private final Memory memory; // to access main memory
    private long accessCounter = 0; // for LRU timestamping

    // stats
    private long hits = 0;
    private long misses = 0;

    public Cache(int cacheSize, int blockSize, int associativity,
                 int hitLatency, int missPenalty,
                 Memory memory) {

        this.cacheSize = cacheSize;
        this.blockSize = blockSize;
        this.associativity = associativity;
        this.hitLatency = hitLatency;
        this.missPenalty = missPenalty;
        this.memory = memory;

        if (cacheSize % (blockSize * associativity) != 0) {
            throw new IllegalArgumentException("Cache size not divisible by blockSize*associativity");
        }

        this.numSets = cacheSize / (blockSize * associativity);
        this.sets = new CacheLine[numSets][associativity];
        for (int s = 0; s < numSets; s++) {
            for (int w = 0; w < associativity; w++) {
                sets[s][w] = new CacheLine(blockSize);
            }
        }
    }

    // ---------- Load (word or double) ----------

    public CacheAccessResult load(long address, boolean isDouble) {
        // compute block number, set index and tag
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        accessCounter++;

        // search for hit
        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // hit (metadata-only cache): update LRU and read from memory (write-through ensures memory is up-to-date)
                line.lruCounter = (int) accessCounter;
                hits++;
                long value = isDouble ? memory.loadDouble(address) : memory.loadWord(address);
                return new CacheAccessResult(value, hitLatency);
            }
        }

        // miss: need to fetch block from memory into an LRU way
        misses++;

        // choose victim: first invalid, else LRU (smallest lruCounter)
        int victim = -1;
        int oldest = Integer.MAX_VALUE;
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (!line.valid) { victim = w; break; }
            if (line.lruCounter < oldest) { oldest = line.lruCounter; victim = w; }
        }

        CacheLine chosen = ways[victim];

        // metadata-only miss: install tag and update LRU; read value from memory
        chosen.valid = true;
        chosen.tag = tag;
        chosen.lruCounter = (int) accessCounter;

        long value = isDouble ? memory.loadDouble(address) : memory.loadWord(address);
        int latency = hitLatency + missPenalty;
        return new CacheAccessResult(value, latency);
    }

    /**
     * Probe expected latency for accessing this address without modifying cache state.
     * If isWrite==true, this is for a store; otherwise a load.
     */
    public int probeLatency(long address, boolean isDouble, boolean isWrite) {
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                return hitLatency;
            }
        }
        return hitLatency + missPenalty;
    }

    /**
     * Perform the actual load and update cache state; this does the block fill
     * if necessary and updates hit/miss stats. Returns loaded value.
     * This method does NOT account for latency (caller must have modeled it).
     */
    public long loadNoLatency(long address, boolean isDouble) {
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        accessCounter++;

        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // hit: update LRU and read from memory (data kept coherent via write-through)
                line.lruCounter = (int) accessCounter;
                hits++;
                return isDouble ? memory.loadDouble(address) : memory.loadWord(address);
            }
        }

        // miss: fetch block into victim
        misses++;
        int victim = -1;
        int oldest = Integer.MAX_VALUE;
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (!line.valid) { victim = w; break; }
            if (line.lruCounter < oldest) { oldest = line.lruCounter; victim = w; }
        }
        CacheLine chosen = ways[victim];
        // miss: install metadata and read from memory
        chosen.valid = true;
        chosen.tag = tag;
        chosen.lruCounter = (int) accessCounter;
        return isDouble ? memory.loadDouble(address) : memory.loadWord(address);
    }

    /**
     * Perform a store (write-through + no-write-allocate) and update cache state.
     * This method performs the write immediately (no latency accounting here).
     */
    public void storeNoLatency(long address, long value, boolean isDouble) {
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        accessCounter++;

        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // hit: update metadata LRU and write through to memory
                line.lruCounter = (int) accessCounter;
                if (isDouble) memory.storeDouble(address, value);
                else memory.storeWord(address, value);
                hits++;
                return;
            }
        }

        // miss: no-write-allocate -> write only to memory
        if (isDouble) memory.storeDouble(address, value);
        else memory.storeWord(address, value);
        misses++;
    }

    // ---------- Store (write-through + no-write-allocate) ----------

    public CacheAccessResult store(long address, long value, boolean isDouble) {
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        accessCounter++;

        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // hit: update metadata LRU and write through to memory
                line.lruCounter = (int) accessCounter;
                if (isDouble) memory.storeDouble(address, value);
                else memory.storeWord(address, value);
                hits++;
                return new CacheAccessResult(0L, hitLatency);
            }
        }

        // miss: write-through + no-write-allocate -> write only to memory
        if (isDouble) memory.storeDouble(address, value);
        else memory.storeWord(address, value);
        misses++;
        int latency = hitLatency + missPenalty;
        return new CacheAccessResult(0L, latency);
    }

    // Note: byte-level block storage removed in metadata-only cache

    public long getHits() { return hits; }
    public long getMisses() { return misses; }

    public int getNumSets() {
        return numSets;
    }

    public CacheLine[][] getSets() {
        return sets;
    }
}
