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
                // hit
                line.lruCounter = (int) accessCounter;
                hits++;
                long value = readValueFromLine(line, address, isDouble);
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

        // load block bytes from memory
        byte[] mem = memory.getRawDataCopy();
        int blockStart = (int) (blockNumber * blockSize);
        int toCopy = Math.min(blockSize, mem.length - blockStart);
        // ensure blockData size
        if (chosen.blockData.length != blockSize) chosen.blockData = new byte[blockSize];
        System.arraycopy(mem, blockStart, chosen.blockData, 0, toCopy);
        chosen.valid = true;
        chosen.tag = tag;
        chosen.lruCounter = (int) accessCounter;

        long value = readValueFromLine(chosen, address, isDouble);
        int latency = hitLatency + missPenalty;
        return new CacheAccessResult(value, latency);
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
                // hit: update cache block and write through to memory
                writeValueToLine(line, address, value, isDouble);
                if (isDouble) memory.storeDouble(address, value);
                else memory.storeWord(address, value);
                line.lruCounter = (int) accessCounter;
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

    private long readValueFromLine(CacheLine line, long address, boolean isDouble) {
        int offset = (int) (address % blockSize);
        if (isDouble) {
            // read 8 bytes big-endian
            long b0 = (line.blockData[offset]     & 0xFFL);
            long b1 = (line.blockData[offset + 1] & 0xFFL);
            long b2 = (line.blockData[offset + 2] & 0xFFL);
            long b3 = (line.blockData[offset + 3] & 0xFFL);
            long b4 = (line.blockData[offset + 4] & 0xFFL);
            long b5 = (line.blockData[offset + 5] & 0xFFL);
            long b6 = (line.blockData[offset + 6] & 0xFFL);
            long b7 = (line.blockData[offset + 7] & 0xFFL);
            return (b0 << 56) | (b1 << 48) | (b2 << 40) | (b3 << 32)
                 | (b4 << 24) | (b5 << 16) | (b6 << 8)  | b7;
        } else {
            int b0 = (line.blockData[offset]     & 0xFF);
            int b1 = (line.blockData[offset + 1] & 0xFF);
            int b2 = (line.blockData[offset + 2] & 0xFF);
            int b3 = (line.blockData[offset + 3] & 0xFF);
            int v = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
            return (long) v;
        }
    }

    private void writeValueToLine(CacheLine line, long address, long value, boolean isDouble) {
        int offset = (int) (address % blockSize);
        if (isDouble) {
            line.blockData[offset]     = (byte) ((value >>> 56) & 0xFF);
            line.blockData[offset + 1] = (byte) ((value >>> 48) & 0xFF);
            line.blockData[offset + 2] = (byte) ((value >>> 40) & 0xFF);
            line.blockData[offset + 3] = (byte) ((value >>> 32) & 0xFF);
            line.blockData[offset + 4] = (byte) ((value >>> 24) & 0xFF);
            line.blockData[offset + 5] = (byte) ((value >>> 16) & 0xFF);
            line.blockData[offset + 6] = (byte) ((value >>> 8)  & 0xFF);
            line.blockData[offset + 7] = (byte) (value & 0xFF);
        } else {
            int v = (int) value;
            line.blockData[offset]     = (byte) ((v >>> 24) & 0xFF);
            line.blockData[offset + 1] = (byte) ((v >>> 16) & 0xFF);
            line.blockData[offset + 2] = (byte) ((v >>> 8)  & 0xFF);
            line.blockData[offset + 3] = (byte) (v & 0xFF);
        }
    }

    public long getHits() { return hits; }
    public long getMisses() { return misses; }

    public int getNumSets() {
        return numSets;
    }

    public CacheLine[][] getSets() {
        return sets;
    }
}
