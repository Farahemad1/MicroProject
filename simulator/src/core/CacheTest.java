package core;

public class CacheTest {

    public static void main(String[] args) throws Exception {
        try {
            testBasicHitMiss();
            testWriteThroughNoAllocate();
            testLRUEviction();
            System.out.println("ALL CACHE TESTS PASSED");
        } catch (AssertionError e) {
            System.err.println("TEST FAILED: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void assertEquals(long a, long b, String msg) {
        if (a != b) throw new AssertionError(msg + ": expected=" + b + " got=" + a);
    }

    private static void assertTrue(boolean c, String msg) {
        if (!c) throw new AssertionError(msg);
    }

    private static void testBasicHitMiss() {
        Memory mem = new Memory();
        Cache cache = new Cache(64, 8, 2, 1, 10, mem);

        long val = 0x0102030405060708L;
        mem.storeDouble(0, val);

        CacheAccessResult r1 = cache.load(0, true);
        // first access should be a miss (hitLatency + missPenalty)
        assertEquals(r1.getLatency(), 11, "basic: first load latency");
        assertEquals(r1.getValue(), val, "basic: first load value");

        CacheAccessResult r2 = cache.load(0, true);
        // second access should be a hit
        assertEquals(r2.getLatency(), 1, "basic: second load latency");

        System.out.println("testBasicHitMiss passed");
    }

    private static void testWriteThroughNoAllocate() {
        Memory mem = new Memory();
        Cache cache = new Cache(64, 8, 2, 1, 10, mem);

        long addr = 8; // next 8-byte aligned block
        long val = 0x1122334455667788L;

        // store via cache (should write through and not allocate)
        CacheAccessResult sres = cache.store(addr, val, true);
        // store reports hitLatency+missPenalty in our implementation for misses
        assertEquals(sres.getLatency(), 11, "store latency");

        // memory must contain the value
        long memVal = mem.loadDouble(addr);
        assertEquals(memVal, val, "memory after store must match");

        // first load should be a miss (no-write-allocate), then next load a hit
        CacheAccessResult r1 = cache.load(addr, true);
        assertEquals(r1.getLatency(), 11, "load after store should miss (no allocate)");
        assertEquals(r1.getValue(), val, "load after store value");

        CacheAccessResult r2 = cache.load(addr, true);
        assertEquals(r2.getLatency(), 1, "subsequent load should be a hit");

        System.out.println("testWriteThroughNoAllocate passed");
    }

    private static void testLRUEviction() {
        Memory mem = new Memory();
        // small cache: 32 bytes, block 8, assoc 2 => numSets = 32/(8*2)=2
        Cache cache = new Cache(32, 8, 2, 1, 5, mem);

        // addresses mapping to the same set (block numbers 0,2,4 => setIndex = blockNumber % 2 == 0)
        long a0 = 0 * 8;   // blockNumber 0
        long a2 = 2 * 8;   // blockNumber 2
        long a4 = 4 * 8;   // blockNumber 4

        long v0 = 100L, v2 = 200L, v4 = 300L;
        mem.storeDouble(a0, v0);
        mem.storeDouble(a2, v2);
        mem.storeDouble(a4, v4);

        // fill two ways: load a0 (miss), load a2 (miss)
        CacheAccessResult r;
        r = cache.load(a0, true); assertEquals(r.getLatency(), 6, "a0 first miss");
        r = cache.load(a2, true); assertEquals(r.getLatency(), 6, "a2 first miss");

        // touch a0 to make a2 the LRU
        r = cache.load(a0, true); assertEquals(r.getLatency(), 1, "a0 hit after ref");

        // now load a4, should evict a2 (LRU)
        r = cache.load(a4, true); assertEquals(r.getLatency(), 6, "a4 miss causes eviction");

        // loading a2 again should be a miss (it was evicted)
        r = cache.load(a2, true); assertEquals(r.getLatency(), 6, "a2 miss after eviction");

        System.out.println("testLRUEviction passed");
    }
}
