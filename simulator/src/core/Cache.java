package core;

/**
 * Set-Associative Cache with Write-Through and No-Write-Allocate Policy.
 * 
 * ============================================================================
 * USER-CONFIGURABLE PARAMETERS:
 * ============================================================================
 * All cache parameters are configurable through the GUI before simulation:
 * 
 * 1. Cache Size (bytes): Total capacity of the cache (e.g., 1024 bytes)
 * 2. Block Size (bytes): Size of each cache line (e.g., 16 bytes)
 * 3. Associativity: Number of ways per set (1=direct-mapped, N=N-way set-associative)
 * 4. Hit Latency (cycles): Cycles required on cache hit (e.g., 1 cycle)
 * 5. Miss Penalty (cycles): Additional cycles on cache miss (e.g., 10 cycles)
 * 
 * ============================================================================
 * ADDRESSING STRATEGY AND CACHE ORGANIZATION:
 * ============================================================================
 * 
 * For any memory address, the cache mapping is computed as follows:
 * 
 *   blockNumber = address / blockSize
 *   setIndex    = blockNumber % numSets
 *   tag         = blockNumber / numSets
 * 
 * WHERE:
 *   - numSets = cacheSize / (blockSize * associativity)
 * 
 * EXAMPLE: Cache = 64 bytes, Block = 8 bytes, Associativity = 2-way
 *   - numSets = 64 / (8 * 2) = 4 sets
 *   - Each set has 2 ways (columns)
 * 
 * For address 25:
 *   - blockNumber = 25 / 8 = 3
 *   - setIndex = 3 % 4 = 3 (maps to set 3)
 *   - tag = 3 / 4 = 0 (tag value to store)
 * 
 * ============================================================================
 * CACHE ACCESS FLOW:
 * ============================================================================
 * 
 * 1. SET SELECTION:
 *    - Use setIndex to directly select which set (row) to check
 *    - All addresses with same (blockNumber % numSets) map to same set
 * 
 * 2. TAG COMPARISON (within selected set):
 *    - Check all ways (associativity) in the selected set
 *    - HIT: If any way has valid=true AND tag matches
 *    - MISS: If no valid matching tag found in any way
 * 
 * 3. ON CACHE MISS - LRU REPLACEMENT:
 *    - Select victim way in the set:
 *      a) First invalid line (if any exists)
 *      b) Otherwise, line with smallest lruCounter (least recently used)
 *    - Install new block metadata (valid=true, tag, lruCounter)
 * 
 * 4. LRU TRACKING:
 *    - Each cache line has lruCounter timestamp
 *    - Updated on every access (hit or miss)
 *    - Smaller lruCounter = older, more likely to be replaced
 * 
 * ============================================================================
 * WRITE POLICY - WRITE-THROUGH + NO-WRITE-ALLOCATE:
 * ============================================================================
 * 
 * WRITE-THROUGH:
 *   - All stores immediately update BOTH cache (if hit) AND main memory
 *   - Ensures cache and memory are always coherent
 *   - No dirty bits needed
 * 
 * NO-WRITE-ALLOCATE:
 *   - On STORE MISS: Write only to memory, do NOT fetch block into cache
 *   - Prevents cache pollution from write-only patterns
 *   - Block will only enter cache on subsequent read (load miss)
 * 
 * ============================================================================
 * CACHE MISSES - DATA ONLY, NOT INSTRUCTIONS:
 * ============================================================================
 * 
 * IMPORTANT: This cache is used ONLY for DATA memory accesses:
 *   - LOAD instructions (L.D, L.S, LD, LW) → may HIT or MISS
 *   - STORE instructions (S.D, S.S, SD, SW) → may HIT or MISS
 * 
 * INSTRUCTION FETCHES are NOT cached:
 *   - Instructions are fetched directly from Program object
 *   - Behave as "always hit" with zero miss penalty
 *   - Only DATA accesses incur cache hit/miss latencies
 * 
 * ============================================================================
 * ADDRESS CLASHES IN TOMASULO:
 * ============================================================================
 * 
 * The TomasuloEngine handles address dependencies correctly:
 * 
 * 1. LOAD vs STORE CLASHES:
 *    - A load cannot execute until all older stores have known addresses
 *    - If older store has same address, load waits for store to complete
 *    - See canLoadExecute() in TomasuloEngine
 * 
 * 2. STORE ORDERING:
 *    - Stores commit in program order via completeFinishedStores()
 *    - Ensures memory consistency
 * 
 * 3. CACHE STATE:
 *    - Cache state is updated at write-back time
 *    - Latency is determined at execution start via probeLatency()
 * 
 * ============================================================================
 */
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

    /**
     * Constructs a cache with user-configurable parameters from the GUI.
     * 
     * @param cacheSize Total cache size in bytes (user-configurable)
     * @param blockSize Cache line size in bytes (user-configurable)
     * @param associativity Ways per set: 1=direct-mapped, N=N-way (user-configurable)
     * @param hitLatency Cycles on cache hit (user-configurable)
     * @param missPenalty Additional cycles on miss beyond hit latency (user-configurable)
     * @param memory Reference to main memory
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

        // Compute number of sets based on user configuration
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
     * Perform a load operation with cache hit/miss simulation.
     * Used by DATA LOADS only (not instruction fetches).
     * 
     * Returns: CacheAccessResult containing value and actual latency
     *   - On HIT: latency = hitLatency
     *   - On MISS: latency = hitLatency + missPenalty
     */
    public CacheAccessResult load(long address, boolean isDouble) {
        // STEP 1: Compute addressing components
        // blockNumber identifies which memory block this address belongs to
        long blockNumber = address / blockSize;
        
        // setIndex determines which set (row) to check - direct mapping
        int setIndex = (int) (blockNumber % numSets);
        
        // tag is the identifier stored in cache line to distinguish blocks
        long tag = blockNumber / numSets;

        accessCounter++;

        // STEP 2: Tag comparison - search all ways in the selected set
        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // CACHE HIT: Block found in cache
                // Update LRU timestamp (most recently used)
                line.lruCounter = (int) accessCounter;
                hits++;
                long value = isDouble ? memory.loadDouble(address) : memory.loadWord(address);
                return new CacheAccessResult(value, hitLatency);
            }
        }

        // STEP 3: CACHE MISS - Block not found in any way of the selected set
        misses++;

        // STEP 4: LRU Replacement - Select victim way in the set
        // Priority: first invalid line, otherwise least recently used
        int victim = -1;
        int oldest = Integer.MAX_VALUE;
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (!line.valid) { victim = w; break; }
            if (line.lruCounter < oldest) { oldest = line.lruCounter; victim = w; }
        }

        CacheLine chosen = ways[victim];

        // STEP 5: Install new block metadata
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

    /**
     * Perform a store operation with cache hit/miss simulation.
     * Used by DATA STORES only.
     * 
     * WRITE-THROUGH POLICY:
     *   - On HIT: Update cache metadata AND write to memory
     *   - On MISS: Write only to memory (no-write-allocate)
     * 
     * NO-WRITE-ALLOCATE POLICY:
     *   - Store misses do NOT fetch the block into cache
     *   - Prevents cache pollution from write-only data
     * 
     * Returns: CacheAccessResult with latency (value unused for stores)
     *   - On HIT: latency = hitLatency
     *   - On MISS: latency = hitLatency + missPenalty
     */
    public CacheAccessResult store(long address, long value, boolean isDouble) {
        // Compute addressing components (same as loads)
        long blockNumber = address / blockSize;
        int setIndex = (int) (blockNumber % numSets);
        long tag = blockNumber / numSets;

        accessCounter++;

        // Check for hit in selected set
        CacheLine[] ways = sets[setIndex];
        for (int w = 0; w < associativity; w++) {
            CacheLine line = ways[w];
            if (line.valid && line.tag == tag) {
                // STORE HIT: Update LRU and write through to memory
                line.lruCounter = (int) accessCounter;
                if (isDouble) memory.storeDouble(address, value);
                else memory.storeWord(address, value);
                hits++;
                return new CacheAccessResult(0L, hitLatency);
            }
        }

        // STORE MISS: No-write-allocate → write ONLY to memory, do NOT fetch block
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
