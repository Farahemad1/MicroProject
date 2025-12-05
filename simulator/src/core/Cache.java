package core;

/**
 * Cache implementation with configurable parameters and write-through + no-write-allocate policy.
 * 
 * CACHE ADDRESSING STRATEGY:
 * ==========================
 * For a given memory address, the cache mapping works as follows:
 * 
 *   blockNumber = address / blockSize
 *   setIndex    = blockNumber % numSets
 *   tag         = blockNumber / numSets
 * 
 * - blockNumber: Identifies which block in memory this address belongs to
 * - setIndex: Determines which set (row) in the cache this block maps to
 * - tag: Stored with each cache line to identify which specific block is cached
 * 
 * SET SELECTION:
 * The setIndex directly determines which set (row) to check. All addresses
 * with the same (blockNumber % numSets) value map to the same set.
 * 
 * TAG COMPARISON:
 * Within the selected set, we check all ways (columns) for a matching tag.
 * If tag matches AND valid bit is set, it's a HIT. Otherwise, it's a MISS.
 * 
 * LRU REPLACEMENT:
 * Each cache line has an lruCounter that tracks when it was last accessed.
 * On a miss, we select the victim as:
 *   1. First invalid line (if any)
 *   2. Otherwise, the line with smallest lruCounter (least recently used)
 * 
 * WRITE POLICY:
 * - Write-through: All stores immediately update main memory
 * - No-write-allocate: On store miss, we write to memory but do NOT fetch the block into cache
 * 
 * INSTRUCTION FETCH:
 * This cache is ONLY used for DATA loads/stores. Instruction fetches are NOT cached
 * and behave as "always hit" (no cache miss penalty for instruction fetches).
 * 
 * CONFIGURABLE PARAMETERS:
 * All cache parameters (size, blockSize, associativity, hitLatency, missPenalty)
 * are user-configurable via constructor and should be passed from simulator configuration.
 */
public class Cache {

    private final int cacheSize;        // Total cache size in bytes
    private final int blockSize;        // Block (line) size in bytes
    private final int associativity;    // Number of ways per set
    private final int numSets;          // Number of sets (computed)

    private final CacheLine[][] sets; // [numSets][associativity]

    private final int hitLatency;       // Cycles added on cache hit
    private final int missPenalty;      // Additional cycles added on cache miss

    private final Memory memory; // to access main memory
    private long accessCounter = 0; // for LRU timestamping

    // stats
    private long hits = 0;
    private long misses = 0;

    /**
     * Constructs a cache with user-configurable parameters.
     * 
     * @param cacheSize Total cache size in bytes
     * @param blockSize Block (cache line) size in bytes
     * @param associativity Number of ways per set (1 = direct-mapped, N = N-way set-associative)
     * @param hitLatency Latency in cycles for a cache hit
     * @param missPenalty Additional latency in cycles for a cache miss (beyond hit latency)
     * @param memory Reference to main memory for fetching blocks
     */
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

        // Compute number of sets: numSets = cacheSize / (blockSize * associativity)
        this.numSets = cacheSize / (blockSize * associativity);
        this.sets = new CacheLine[numSets][associativity];
        for (int s = 0; s < numSets; s++) {
            for (int w = 0; w < associativity; w++) {
                sets[s][w] = new CacheLine(blockSize);
            }
        }
    }

    // ---------- Load (word or double) ----------

    /**
     * Performs a load operation with full cache simulation.
     * 
     * ADDRESSING STRATEGY IN ACTION:
     * 1. blockNumber = address / blockSize  → identifies which memory block
     * 2. setIndex = blockNumber % numSets   → determines which set to check
     * 3. tag = blockNumber / numSets        → stored value to identify block
     * 
     * HIT: If valid && tag matches in the selected set → return hitLatency
     * MISS: Otherwise, apply LRU replacement → return (hitLatency + missPenalty)
     */
    public CacheAccessResult load(long address, boolean isDouble) {
        // STEP 1: Compute block number from address
        long blockNumber = address / blockSize;
        
        // STEP 2: Set selection - determines which set (row) to check
        int setIndex = (int) (blockNumber % numSets);
        
        // STEP 3: Tag computation - used for comparison within the set
        long tag = blockNumber / numSets;

        accessCounter++;

        // STEP 4: Tag comparison - search all ways in the selected set for a hit
        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // HIT: Update LRU timestamp and read from memory
                // (metadata-only cache: write-through ensures memory is up-to-date)
                line.lruCounter = (int) accessCounter;
                hits++;
                long value = isDouble ? memory.loadDouble(address) : memory.loadWord(address);
                return new CacheAccessResult(value, hitLatency);
            }
        }

        // MISS: Block not found in cache, need to fetch from memory
        misses++;

        // STEP 5: LRU replacement - choose victim line
        // Priority: first invalid line, otherwise least recently used (smallest lruCounter)
        int victim = -1;
        int oldest = Integer.MAX_VALUE;
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (!line.valid) { victim = w; break; }
            if (line.lruCounter < oldest) { oldest = line.lruCounter; victim = w; }
        }

        CacheLine chosen = ways[victim];

        // STEP 6: Install new block metadata (metadata-only cache)
        chosen.valid = true;
        chosen.tag = tag;
        chosen.lruCounter = (int) accessCounter;

        // Fetch data from memory and return with miss penalty
        long value = isDouble ? memory.loadDouble(address) : memory.loadWord(address);
        int latency = hitLatency + missPenalty;
        return new CacheAccessResult(value, latency);
    }

    /**
     * Probe expected latency for accessing this address WITHOUT modifying cache state.
     * This is used by TomasuloEngine to determine execution time before starting load/store.
     * 
     * USAGE IN TOMASULO:
     * - When a load/store is ready to execute, call this to determine latency
     * - effectiveLatency = baseLatency + probeLatency(address, isDouble, isWrite)
     * - This latency determines how long the load/store will execute
     * 
     * @param address Memory address to probe
     * @param isDouble Whether this is a double (8 bytes) or word (4 bytes) access
     * @param isWrite true for stores, false for loads (currently same logic for both)
     * @return hitLatency if block is in cache, (hitLatency + missPenalty) if not
     */
    public int probeLatency(long address, boolean isDouble, boolean isWrite) {
        // Apply same addressing strategy: blockNumber → setIndex → tag
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        // Check if block is present in the selected set
        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // Block is in cache - return hit latency
                return hitLatency;
            }
        }
        // Block not in cache - return hit latency + miss penalty
        return hitLatency + missPenalty;
    }

    /**
     * Perform the actual load and update cache state; this does the block fill
     * if necessary and updates hit/miss stats. Returns loaded value.
     * 
     * USAGE IN TOMASULO:
     * This method is called at WRITE-BACK time (after execution completes).
     * Latency has already been modeled during execution via probeLatency().
     * This method performs the actual state update and returns the value.
     * 
     * @param address Memory address to load from
     * @param isDouble Whether this is a double (8 bytes) or word (4 bytes) access
     * @return The loaded value from memory
     */
    public long loadNoLatency(long address, boolean isDouble) {
        // Apply addressing strategy
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        accessCounter++;

        // Check for hit
        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // HIT: Update LRU and read from memory (data kept coherent via write-through)
                line.lruCounter = (int) accessCounter;
                hits++;
                return isDouble ? memory.loadDouble(address) : memory.loadWord(address);
            }
        }

        // MISS: Apply LRU replacement and fetch block
        misses++;
        int victim = -1;
        int oldest = Integer.MAX_VALUE;
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (!line.valid) { victim = w; break; }
            if (line.lruCounter < oldest) { oldest = line.lruCounter; victim = w; }
        }
        CacheLine chosen = ways[victim];
        // Install new block metadata
        chosen.valid = true;
        chosen.tag = tag;
        chosen.lruCounter = (int) accessCounter;
        return isDouble ? memory.loadDouble(address) : memory.loadWord(address);
    }

    /**
     * Perform a store (write-through + no-write-allocate) and update cache state.
     * 
     * WRITE POLICY:
     * - Write-through: All stores immediately update main memory (even on hit)
     * - No-write-allocate: On MISS, we do NOT fetch the block into cache
     *   → This prevents unnecessary block fills for write-only patterns
     * 
     * USAGE IN TOMASULO:
     * This method is called when a store completes execution (in completeFinishedStores).
     * Latency has already been modeled during execution via probeLatency().
     * This method performs the actual memory write and cache state update.
     * 
     * @param address Memory address to store to
     * @param value Value to store
     * @param isDouble Whether this is a double (8 bytes) or word (4 bytes) access
     */
    public void storeNoLatency(long address, long value, boolean isDouble) {
        // Apply addressing strategy
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        accessCounter++;

        // Check for hit
        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // HIT: Update LRU metadata and write through to memory
                line.lruCounter = (int) accessCounter;
                if (isDouble) memory.storeDouble(address, value);
                else memory.storeWord(address, value);
                hits++;
                return;
            }
        }

        // MISS: No-write-allocate → write ONLY to memory, do NOT fetch block into cache
        // This avoids polluting cache with write-only data
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
