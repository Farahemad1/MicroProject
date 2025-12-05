package core;

public class CacheAccessResult {
    private final long value;
    private final int latency;

    public CacheAccessResult(long value, int latency) {
        this.value = value;
        this.latency = latency;
    }

    public long getValue() {
        return value;
    }

    public int getLatency() {
        return latency;
    }
}
