package core;

public class Memory {

    private static final int MEM_SIZE = 4096; // 4 KB
    private final byte[] data = new byte[MEM_SIZE];

    public Memory() {
        reset();
    }

    public void reset() {
        for (int i = 0; i < MEM_SIZE; i++) {
            data[i] = 0;
        }
    }

    private void checkAddress(long address, int size) {
        if (address < 0 || address + size > MEM_SIZE) {
            throw new IllegalArgumentException("Memory access out of bounds at address " + address);
        }
    }

    // ---------- 4-byte word (for LW, SW, L.S, S.S) ----------

    public long loadWord(long address) {
        // assume naturally aligned (multiple of 4)
        checkAddress(address, 4);
        int addr = (int) address;
        int b0 = (data[addr]     & 0xFF);
        int b1 = (data[addr + 1] & 0xFF);
        int b2 = (data[addr + 2] & 0xFF);
        int b3 = (data[addr + 3] & 0xFF);

        // treat as signed 32-bit then extend to 64-bit
        int value = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        return (long) value;
    }

    public void storeWord(long address, long value) {
        checkAddress(address, 4);
        int addr = (int) address;
        int v = (int) value; // low 32 bits
        data[addr]     = (byte) ((v >>> 24) & 0xFF);
        data[addr + 1] = (byte) ((v >>> 16) & 0xFF);
        data[addr + 2] = (byte) ((v >>> 8)  & 0xFF);
        data[addr + 3] = (byte) (v & 0xFF);
    }

    // ---------- 8-byte double/64-bit (for LD, SD, L.D, S.D) ----------

    public long loadDouble(long address) {
        // assume naturally aligned (multiple of 8)
        checkAddress(address, 8);
        int addr = (int) address;

        long b0 = (data[addr]     & 0xFFL);
        long b1 = (data[addr + 1] & 0xFFL);
        long b2 = (data[addr + 2] & 0xFFL);
        long b3 = (data[addr + 3] & 0xFFL);
        long b4 = (data[addr + 4] & 0xFFL);
        long b5 = (data[addr + 5] & 0xFFL);
        long b6 = (data[addr + 6] & 0xFFL);
        long b7 = (data[addr + 7] & 0xFFL);

        return (b0 << 56) | (b1 << 48) | (b2 << 40) | (b3 << 32)
             | (b4 << 24) | (b5 << 16) | (b6 << 8)  | b7;
    }

    public void storeDouble(long address, long value) {
        checkAddress(address, 8);
        int addr = (int) address;

        data[addr]     = (byte) ((value >>> 56) & 0xFF);
        data[addr + 1] = (byte) ((value >>> 48) & 0xFF);
        data[addr + 2] = (byte) ((value >>> 40) & 0xFF);
        data[addr + 3] = (byte) ((value >>> 32) & 0xFF);
        data[addr + 4] = (byte) ((value >>> 24) & 0xFF);
        data[addr + 5] = (byte) ((value >>> 16) & 0xFF);
        data[addr + 6] = (byte) ((value >>> 8)  & 0xFF);
        data[addr + 7] = (byte) (value & 0xFF);
    }

    // For cache fills / debugging
    public byte[] getRawDataCopy() {
        return data.clone();
    }
}