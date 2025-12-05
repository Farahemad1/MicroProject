package core;

public class StoreBufferEntry {

    private final String name;   // e.g. "S0", "S1"

    private boolean busy;

    // Address computation
    private long address;
    private String addressQ;
    private int baseRegIndex;
    private long offset;

    // Value to store
    private long value;
    private String valueQ;       // RS / LB name if value pending

    private int remainingCycles;
    private Instruction instruction;

    public StoreBufferEntry(String name) {
        this.name = name;
        clear();
    }

    public void clear() {
        busy = false;
        address = 0;
        addressQ = null;
        baseRegIndex = -1;
        offset = 0;
        value = 0;
        valueQ = null;
        remainingCycles = 0;
        instruction = null;
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

    public long getValue() { return value; }
    public void setValue(long value) { this.value = value; }

    public String getValueQ() { return valueQ; }
    public void setValueQ(String valueQ) { this.valueQ = valueQ; }

    public int getRemainingCycles() { return remainingCycles; }
    public void setRemainingCycles(int remainingCycles) { this.remainingCycles = remainingCycles; }

    public Instruction getInstruction() { return instruction; }
    public void setInstruction(Instruction instruction) { this.instruction = instruction; }

    public boolean isAddressReady() { return addressQ == null; }
    public boolean isValueReady() { return valueQ == null; }

    public boolean isExecuting() {
        return busy && remainingCycles > 0;
    }
}
