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
        // TODO: implement real N-way set associative logic with LRU.
        // For now, just bypass cache to memory with miss latency
        int latency = hitLatency + missPenalty;
        long value = isDouble
                ? memory.loadDouble(address)
                : memory.loadWord(address);
        return new CacheAccessResult(value, latency);
    }

    // ---------- Store (write-through + no-write-allocate) ----------

    public CacheAccessResult store(long address, long value, boolean isDouble) {
        // TODO: once cache is fully implemented, handle hit/miss properly.
        // For now: write directly to memory, treat as miss.
        if (isDouble) {
            memory.storeDouble(address, value);
        } else {
            memory.storeWord(address, value);
        }
        int latency = hitLatency + missPenalty;
        return new CacheAccessResult(0L, latency);
    }

    public int getNumSets() {
        return numSets;
    }

    public CacheLine[][] getSets() {
        return sets;
    }
}
