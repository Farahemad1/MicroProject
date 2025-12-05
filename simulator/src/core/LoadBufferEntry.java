//package core;
//
//public class LoadBufferEntry {
//
//    private final String name;   // e.g. "L0", "L1"
//
//    private boolean busy;
//
//    // Address computation
//    private long address;        // effective address when known
//    private String addressQ;     // RS name if address depends on some result
//    private int baseRegIndex;    // R index for base
//    private long offset;         // offset
//
//    // Execution
//    private int remainingCycles; // >0 when executing
//    private Instruction instruction;
//
//    private String destReg;      // "R5" or "F2" for the loaded value
//    private long value;          // value loaded from memory
//
//    public LoadBufferEntry(String name) {
//        this.name = name;
//        clear();
//    }
//
//    public void clear() {
//        busy = false;
//        address = 0;
//        addressQ = null;
//        baseRegIndex = -1;
//        offset = 0;
//        remainingCycles = 0;
//        instruction = null;
//        destReg = null;
//        value = 0;
//    }
//
//    public String getName() { return name; }
//
//    public boolean isBusy() { return busy; }
//    public void setBusy(boolean busy) { this.busy = busy; }
//
//    public long getAddress() { return address; }
//    public void setAddress(long address) { this.address = address; }
//
//    public String getAddressQ() { return addressQ; }
//    public void setAddressQ(String addressQ) { this.addressQ = addressQ; }
//
//    public int getBaseRegIndex() { return baseRegIndex; }
//    public void setBaseRegIndex(int baseRegIndex) { this.baseRegIndex = baseRegIndex; }
//
//    public long getOffset() { return offset; }
//    public void setOffset(long offset) { this.offset = offset; }
//
//    public int getRemainingCycles() { return remainingCycles; }
//    public void setRemainingCycles(int remainingCycles) { this.remainingCycles = remainingCycles; }
//
//    public Instruction getInstruction() { return instruction; }
//    public void setInstruction(Instruction instruction) { this.instruction = instruction; }
//
//    public String getDestReg() { return destReg; }
//    public void setDestReg(String destReg) { this.destReg = destReg; }
//
//    public long getValue() { return value; }
//    public void setValue(long value) { this.value = value; }
//
//    public boolean isAddressReady() {
//        return addressQ == null;
//    }
//
//    public boolean isExecuting() {
//        return busy && remainingCycles > 0;
//    }
//}
package core;

public class LoadBufferEntry {

    private final String name;   // e.g. "L0", "L1"

    private boolean busy;

    // Address computation
    private long address;        // effective address when known
    private String addressQ;     // RS name if address depends on some result
    private int baseRegIndex;    // R index for base
    private long offset;         // offset

    // Execution
    private int remainingCycles; // >0 when executing
    private Instruction instruction;

    private String destReg;      // "R5" or "F0" for the loaded value
    private long value;          // value loaded from memory

    public LoadBufferEntry(String name) {
        this.name = name;
        clear();
    }

    public void clear() {
        busy = false;
        address = 0;
        addressQ = null;
        baseRegIndex = -1;
        offset = 0;
        remainingCycles = 0;
        instruction = null;
        destReg = null;
        value = 0;
    }

    public String getName() { return name; }

    public boolean isBusy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }

    public long getAddress() { return address; }
    public void setAddress(long address) { this.address = address; }

    public String getAddressQ() { return addressQ; }
    public void setAddressQ(String addressQ) { this.addressQ = addressQ; }

    public int getBaseRegIndex() { return baseRegIndex; }
    public void setBaseRegIndex(int baseRegIndex) { this.baseRegIndex = baseRegIndex; }

    public long getOffset() { return offset; }
    public void setOffset(long offset) { this.offset = offset; }

    public int getRemainingCycles() { return remainingCycles; }
    public void setRemainingCycles(int remainingCycles) { this.remainingCycles = remainingCycles; }

    public Instruction getInstruction() { return instruction; }
    public void setInstruction(Instruction instruction) { this.instruction = instruction; }

    public String getDestReg() { return destReg; }
    public void setDestReg(String destReg) { this.destReg = destReg; }

    public long getValue() { return value; }
    public void setValue(long value) { this.value = value; }

    public boolean isAddressReady() {
        return addressQ == null;
    }

    public boolean isExecuting() {
        // THIS is what the engine expects:
        return busy && remainingCycles > 0;
    }
}
