package core;

public class CacheLine {
    boolean valid;
    long tag;
    int lruCounter;

    public CacheLine(int blockSize){
        // blockSize parameter kept for compatibility but not used in metadata-only model
        this.valid = false;
        this.tag = 0;
        this.lruCounter = 0;
    }

    public boolean isValid() { return valid; }
    public long getTag() { return tag; }
    public int getLruCounter() { return lruCounter; }
}