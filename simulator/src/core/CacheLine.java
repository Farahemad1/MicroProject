package core;

public class CacheLine {
    boolean valid;
    long tag;
    byte[] blockData;
    int lruCounter;

    public CacheLine(int blockSize){
    	this.valid=false;
    	this.tag=0;
    	this.blockData=new byte[blockSize];
    	this.lruCounter=0;
    }

    public boolean isValid() { return valid; }
    public long getTag() { return tag; }
    public int getLruCounter() { return lruCounter; }
}