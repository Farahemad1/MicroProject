package core;

public class RegisterFile {

    private final long[] intRegs = new long[32]; // R0–R31
    private final long[] fpRegs  = new long[32]; // F0–F31

    public RegisterFile() {
        reset();
    }

    public void reset() {
        for (int i = 0; i < 32; i++) {
            intRegs[i] = 0;
            fpRegs[i] = 0;
        }
    }

    // ---------- Integer registers ----------

    public long getInt(int index) {
        if (index < 0 || index >= 32) {
            throw new IllegalArgumentException("Invalid int register index: R" + index);
        }
        // R0 is hard-wired to 0
        if (index == 0) return 0;
        return intRegs[index];
    }

    public void setInt(int index, long value) {
        if (index < 0 || index >= 32) {
            throw new IllegalArgumentException("Invalid int register index: R" + index);
        }
        if (index == 0) {
            // ignore writes to R0
            return;
        }
        intRegs[index] = value;
    }

    // ---------- FP registers ----------

    public long getFp(int index) {
        if (index < 0 || index >= 32) {
            throw new IllegalArgumentException("Invalid FP register index: F" + index);
        }
        return fpRegs[index];
    }

    public void setFp(int index, long value) {
        if (index < 0 || index >= 32) {
            throw new IllegalArgumentException("Invalid FP register index: F" + index);
        }
        fpRegs[index] = value;
    }

    // For snapshots / GUI
    public long[] getIntRegsCopy() {
        return intRegs.clone();
    }

    public long[] getFpRegsCopy() {
        return fpRegs.clone();
    }
}