package core;

public class ReservationStation {

    private final String name;      // e.g. "A0", "M1", "I2"
    private final RSCategory category;

    private boolean busy;
    private InstructionType op;

    private long Vj;
    private long Vk;
    private String Qj;              // RS name that will produce Vj (null if ready)
    private String Qk;              // RS name that will produce Vk (null if ready)

    private long A;                 // immediate / address offset / misc
    private String dest;            // destination register like "R5" or "F2" (for CDB commit)

    private int remainingCycles;    // >0 when executing
    private int executionLatency;   // constant for this op when started

    private Instruction instruction;  // link to original instruction (for timing table)

    public ReservationStation(String name, RSCategory category) {
        this.name = name;
        this.category = category;
        clear();
    }

    public void clear() {
        busy = false;
        op = null;
        Vj = 0;
        Vk = 0;
        Qj = null;
        Qk = null;
        A = 0;
        dest = null;
        remainingCycles = 0;
        executionLatency = 0;
        instruction = null;
    }

    // --------- Getters / setters ----------

    public String getName() { return name; }
    public RSCategory getCategory() { return category; }

    public boolean isBusy() { return busy; }
    public void setBusy(boolean busy) { this.busy = busy; }

    public InstructionType getOp() { return op; }
    public void setOp(InstructionType op) { this.op = op; }

    public long getVj() { return Vj; }
    public void setVj(long vj) { Vj = vj; }

    public long getVk() { return Vk; }
    public void setVk(long vk) { Vk = vk; }

    public String getQj() { return Qj; }
    public void setQj(String qj) { Qj = qj; }

    public String getQk() { return Qk; }
    public void setQk(String qk) { Qk = qk; }

    public long getA() { return A; }
    public void setA(long a) { A = a; }

    public String getDest() { return dest; }
    public void setDest(String dest) { this.dest = dest; }

    public int getRemainingCycles() { return remainingCycles; }
    public void setRemainingCycles(int remainingCycles) { this.remainingCycles = remainingCycles; }

    public int getExecutionLatency() { return executionLatency; }
    public void setExecutionLatency(int executionLatency) { this.executionLatency = executionLatency; }

    public Instruction getInstruction() { return instruction; }
    public void setInstruction(Instruction instruction) { this.instruction = instruction; }

    public boolean isReady() {
        return busy && Qj == null && Qk == null && remainingCycles == 0;
    }

    public boolean isExecuting() {
        return busy && remainingCycles > 0;
    }
}
