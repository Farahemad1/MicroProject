package core;

public class Instruction {
    private final InstructionType type;
    private final String rawText;
    private final int pcIndex;   // index in program list
 // for memory instructions: did the reg operand start with F or R?
    private boolean memRegIsFp = false;
    
    public boolean isMemRegFp() {
        return memRegIsFp;
    }

    public void setMemRegIsFp(boolean memRegIsFp) {
        this.memRegIsFp = memRegIsFp;
    }

    // operands (integers represent reg indices or immediates)
    private int rd = -1;
    private int rs = -1;
    private int rt = -1;
    private long immediate = 0;      // offset, imm, or target index

    // label on this line (e.g., "LOOP:")
    private String label;

    // branch target label (if textual)
    private String branchLabel;

    // timing info
    private int issueCycle = -1;
    private int startExecCycle = -1;
    private int endExecCycle = -1;
    private int writeBackCycle = -1;

    public Instruction(InstructionType type, String rawText, int pcIndex) {
        this.type = type;
        this.rawText = rawText;
        this.pcIndex = pcIndex;
    }

    // getters and setters...

    public InstructionType getType() { return type; }
    public String getRawText() { return rawText; }
    public int getPcIndex() { return pcIndex; }

    public int getRd() { return rd; }
    public void setRd(int rd) { this.rd = rd; }

    public int getRs() { return rs; }
    public void setRs(int rs) { this.rs = rs; }

    public int getRt() { return rt; }
    public void setRt(int rt) { this.rt = rt; }

    public long getImmediate() { return immediate; }
    public void setImmediate(long immediate) { this.immediate = immediate; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getBranchLabel() { return branchLabel; }
    public void setBranchLabel(String branchLabel) { this.branchLabel = branchLabel; }

    public int getIssueCycle() { return issueCycle; }
    public void setIssueCycle(int issueCycle) { this.issueCycle = issueCycle; }

    public int getStartExecCycle() { return startExecCycle; }
    public void setStartExecCycle(int startExecCycle) { this.startExecCycle = startExecCycle; }

    public int getEndExecCycle() { return endExecCycle; }
    public void setEndExecCycle(int endExecCycle) { this.endExecCycle = endExecCycle; }

    public int getWriteBackCycle() { return writeBackCycle; }
    public void setWriteBackCycle(int writeBackCycle) { this.writeBackCycle = writeBackCycle; }
}
