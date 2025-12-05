package core;

import java.util.ArrayList;
import java.util.List;

public class TomasuloEngine {

    private final Program program;

    
    private final RegisterFile registers;
    private final RegisterStatus regStatus;
    private final Memory memory;
    private final Cache cache;

    private final List<ReservationStation> fpAddStations;
    private final List<ReservationStation> fpMulStations;
    private final List<ReservationStation> intAluStations;
    private final List<LoadBufferEntry> loadBuffers;
    private final List<StoreBufferEntry> storeBuffers;

    private boolean fetchStalled; 
    private int pc;               // index into program
    private int currentCycle;

    private final List<CycleState> history;

    // Config (latencies, sizes, etc.)
    private final int fpAddLatency;
    private final int fpMulLatency;
    private final int intAluLatency;
    private final int loadLatencyBase;  // we’ll combine with cache latency
    private final int storeLatencyBase;

    public TomasuloEngine(
            Program program,
            RegisterFile registers,
            RegisterStatus regStatus,
            Memory memory,
            Cache cache,
            int numFpAddRS,
            int numFpMulRS,
            int numIntAluRS,
            int numLoadBuffers,
            int numStoreBuffers,
            int fpAddLatency,
            int fpMulLatency,
            int intAluLatency,
            int loadLatencyBase,
            int storeLatencyBase
    ) {
        this.program = program;
        this.registers = registers;
        this.regStatus = regStatus;
        this.memory = memory;
        this.cache = cache;

        this.fpAddLatency = fpAddLatency;
        this.fpMulLatency = fpMulLatency;
        this.intAluLatency = intAluLatency;
        this.loadLatencyBase = loadLatencyBase;
        this.storeLatencyBase = storeLatencyBase;

        this.fpAddStations = new ArrayList<>();
        this.fpMulStations = new ArrayList<>();
        this.intAluStations = new ArrayList<>();
        this.loadBuffers = new ArrayList<>();
        this.storeBuffers = new ArrayList<>();

        for (int i = 0; i < numFpAddRS; i++) {
            fpAddStations.add(new ReservationStation("A" + i, RSCategory.FP_ADD));
        }
        for (int i = 0; i < numFpMulRS; i++) {
            fpMulStations.add(new ReservationStation("M" + i, RSCategory.FP_MUL));
        }
        for (int i = 0; i < numIntAluRS; i++) {
            intAluStations.add(new ReservationStation("I" + i, RSCategory.INT_ALU));
        }
        for (int i = 0; i < numLoadBuffers; i++) {
            loadBuffers.add(new LoadBufferEntry("L" + i));
        }
        for (int i = 0; i < numStoreBuffers; i++) {
            storeBuffers.add(new StoreBufferEntry("S" + i));
        }

        this.history = new ArrayList<>();

        reset();
    }

    public void reset() {
        pc = 0;
        currentCycle = 0;
        fetchStalled = false;

        registers.reset();
        regStatus.reset();
        memory.reset();
        // cache state is not fully defined yet, but you could clear it later

        for (ReservationStation rs : fpAddStations) rs.clear();
        for (ReservationStation rs : fpMulStations) rs.clear();
        for (ReservationStation rs : intAluStations) rs.clear();
        for (LoadBufferEntry lb : loadBuffers) lb.clear();
        for (StoreBufferEntry sb : storeBuffers) sb.clear();

        history.clear();
        history.add(takeSnapshot());
    }

    public int getCurrentCycle() {
        return currentCycle;
    }

    public int getPc() {
        return pc;
    }

    public CycleState getCurrentState() {
        return history.get(history.size() - 1);
    }

    public void nextCycle() {
        // Advance global cycle counter
        currentCycle++;

        // 1) Decrement remainingCycles for all executing RS / loads / stores
        advanceExecuting();

        // 2) Commit any finished stores (stores don't use CDB)
        completeFinishedStores();

        // 3) Do one CDB broadcast this cycle (either an INT ALU result or a LOAD result)
        handleWriteBack();

        // 4) Start execution for any RS / loads / stores that are now ready
        startReadyExecutions();

        // 5) Issue at most one new instruction (respecting branch stall)
        issueInstruction();

        // 6) Save snapshot for GUI / debugging
        history.add(takeSnapshot());
    }


    public void previousCycle() {
        if (history.size() <= 1) {
            return;
        }
        // Pop last snapshot
        history.remove(history.size() - 1);
        CycleState prev = history.get(history.size() - 1);
        currentCycle = prev.getCycleNumber();
        pc = prev.getPc();
        // We don’t restore full internal state yet – we will later
    }
    
    private void completeFinishedStores() {
        for (StoreBufferEntry sb : storeBuffers) {
            if (!sb.isBusy()) continue;
            if (sb.getRemainingCycles() > 0) continue;

            Instruction instr = sb.getInstruction();
            if (instr == null) continue;
            if (instr.getWriteBackCycle() != -1) continue; // already committed

            // Perform the actual memory write
            InstructionType t = instr.getType();
            boolean isD = isDouble(t);
            long addr = sb.getAddress();
            long val = sb.getValue();

            if (isD) {
                memory.storeDouble(addr, val);
            } else {
                memory.storeWord(addr, val);
            }

            // Mark WB cycle for timing table
            instr.setWriteBackCycle(currentCycle);

            // Free this store buffer entry
            sb.clear();
        }
    }

    
    private void handleWriteBack() {
        // ---- 1) Collect integer ALU RS candidates (finished this cycle, not yet WB) ----
        List<ReservationStation> rsCandidates = new ArrayList<>();
        for (ReservationStation rs : intAluStations) {
            if (rs.isBusy()
                    && rs.getOp() != null
                    && rs.getRemainingCycles() == 0
                    && rs.getInstruction() != null
                    && rs.getInstruction().getWriteBackCycle() == -1) {
                rsCandidates.add(rs);
            }
        }

        // ---- 2) Collect LOAD buffer candidates (finished this cycle, not yet WB) ----
        List<LoadBufferEntry> loadCandidates = new ArrayList<>();
        for (LoadBufferEntry lb : loadBuffers) {
            if (lb.isBusy()
                    && lb.getRemainingCycles() == 0
                    && lb.getInstruction() != null
                    && lb.getInstruction().getWriteBackCycle() == -1) {
                loadCandidates.add(lb);
            }
        }

        // If nothing finished this cycle, nothing to broadcast
        if (rsCandidates.isEmpty() && loadCandidates.isEmpty()) {
            return;
        }
        System.out.println("handleWriteBack: cycle " + currentCycle
                + " rsCandidates=" + rsCandidates.size()
                + " loadCandidates=" + loadCandidates.size());

        // ---- 3) Choose the best producer for the single CDB this cycle ----
        String bestName = null;
        Instruction bestInstr = null;

        boolean bestIsRS = false;
        ReservationStation bestRS = null;
        LoadBufferEntry bestLB = null;

        int bestScore = -1;
        int bestIssueCycle = Integer.MAX_VALUE;

        // 3a) Check RS producers
        for (ReservationStation rs : rsCandidates) {
            String name = rs.getName();
            Instruction instr = rs.getInstruction();
            int score = countDependents(name);           // how many wait on this producer
            int issue = instr.getIssueCycle();           // for FIFO tie-break

            if (score > bestScore || (score == bestScore && issue < bestIssueCycle)) {
                bestScore = score;
                bestIssueCycle = issue;
                bestName = name;
                bestInstr = instr;
                bestIsRS = true;
                bestRS = rs;
                bestLB = null;
            }
        }

        // 3b) Check LOAD producers
        for (LoadBufferEntry lb : loadCandidates) {
            String name = lb.getName();
            Instruction instr = lb.getInstruction();
            int score = countDependents(name);
            int issue = instr.getIssueCycle();

            if (score > bestScore || (score == bestScore && issue < bestIssueCycle)) {
                bestScore = score;
                bestIssueCycle = issue;
                bestName = name;
                bestInstr = instr;
                bestIsRS = false;
                bestRS = null;
                bestLB = lb;
            }
        }
        if (bestLB != null) {
            System.out.println("handleWriteBack: chose LOAD producer "
                    + bestLB.getName()
                    + " dest=" + bestLB.getDestReg()
                    + " value=" + bestLB.getValue());
        }
        if (bestInstr == null) {
            return; // should not happen, but safe guard
        }

        // ---- 4) Actually perform the write-back for the chosen producer ----
        if (bestIsRS) {
            InstructionType op = bestRS.getOp();

            if (op == InstructionType.BEQ || op == InstructionType.BNE) {
                // Branch: resolve, set PC, unstall fetch
                handleBranchWriteBack(bestRS, bestInstr);
            } else {
                // Normal integer ALU result: compute + write to regs + broadcast
                handleIntAluWriteBack(bestRS, bestInstr);
            }

            bestInstr.setWriteBackCycle(currentCycle);
            bestRS.clear();
        } else {
            // Load result: write loaded value to dest reg + broadcast
        	 System.out.println("handleWriteBack: calling handleLoadWriteBack for "
        	            + bestLB.getName()
        	            + " dest=" + bestLB.getDestReg()
        	            + " value=" + bestLB.getValue());
            handleLoadWriteBack(bestLB, bestInstr);

            bestInstr.setWriteBackCycle(currentCycle);
            bestLB.clear();
        }
    }

    private int countDependents(String producerName) {
        int count = 0;

        for (ReservationStation rs : fpAddStations) {
            if (producerName.equals(rs.getQj()) || producerName.equals(rs.getQk())) count++;
        }
        for (ReservationStation rs : fpMulStations) {
            if (producerName.equals(rs.getQj()) || producerName.equals(rs.getQk())) count++;
        }
        for (ReservationStation rs : intAluStations) {
            if (producerName.equals(rs.getQj()) || producerName.equals(rs.getQk())) count++;
        }

        for (LoadBufferEntry lb : loadBuffers) {
            if (producerName.equals(lb.getAddressQ())) count++;
        }
        for (StoreBufferEntry sb : storeBuffers) {
            if (producerName.equals(sb.getAddressQ())
                    || producerName.equals(sb.getValueQ())) count++;
        }

        return count;
    }

    private void handleIntAluWriteBack(ReservationStation rs, Instruction instr) {
        long result = 0;
        InstructionType op = rs.getOp();

        if (op == InstructionType.DADDI) {
            result = rs.getVj() + rs.getA();
        } else if (op == InstructionType.DSUBI) {
            result = rs.getVj() - rs.getA();
        } else {
            // later: handle other int ops
        }

        // 1) Broadcast to registers if this RS owns a dest
        String dest = rs.getDest();
        if (dest != null) {
            if (dest.startsWith("R")) {
                int idx = Integer.parseInt(dest.substring(1));
                if (regStatus.getIntOwner(idx) != null
                        && regStatus.getIntOwner(idx).equals(rs.getName())) {
                    registers.setInt(idx, result);
                    regStatus.setIntOwner(idx, null);
                }
            } else if (dest.startsWith("F")) {
                int idx = Integer.parseInt(dest.substring(1));
                if (regStatus.getFpOwner(idx) != null
                        && regStatus.getFpOwner(idx).equals(rs.getName())) {
                    registers.setFp(idx, result);
                    regStatus.setFpOwner(idx, null);
                }
            }
        }

        // 2) Broadcast to waiting RS / buffers
        broadcastToWaiters(rs.getName(), result);
    }

    private void handleLoadWriteBack(LoadBufferEntry lb, Instruction instr) {
        // 1) Compute the value from memory *now* (after stores have committed this cycle)
        long addr = lb.getAddress();
        InstructionType t = instr.getType();
        boolean isD = isDouble(t);
        long result = isD ? memory.loadDouble(addr) : memory.loadWord(addr);

        String dest = lb.getDestReg();

        // DEBUG (optional):
        // System.out.println("handleLoadWriteBack: LB=" + lb.getName()
        //         + " dest=" + dest + " addr=" + addr + " result=" + result);

        // 2) Write result into the destination register (if still owned by this load)
        if (dest != null) {
            if (dest.startsWith("R")) {
                int idx = Integer.parseInt(dest.substring(1));
                if (idx != 0 && lb.getName().equals(regStatus.getIntOwner(idx))) {
                    registers.setInt(idx, result);
                    regStatus.setIntOwner(idx, null);
                }
            } else if (dest.startsWith("F")) {
                int idx = Integer.parseInt(dest.substring(1));
                if (lb.getName().equals(regStatus.getFpOwner(idx))) {
                    registers.setFp(idx, result);
                    regStatus.setFpOwner(idx, null);
                }
            }
        }

        // 3) Broadcast load result on CDB to wake up any dependents
        broadcastToWaiters(lb.getName(), result);
    }


    
    
    private void handleBranchWriteBack(ReservationStation rs, Instruction instr) {
        long vj = rs.getVj();
        long vk = rs.getVk();
        boolean taken;

        if (rs.getOp() == InstructionType.BEQ) {
            taken = (vj == vk);
        } else { // BNE
            taken = (vj != vk);
        }

        if (taken) {
            // A holds absolute target PC index
            pc = (int) rs.getA();
        }
        // if not taken, PC already points to sequential next (we advanced on issue)

        // Branch resolved -> resume fetch
        fetchStalled = false;
    }

    private void broadcastToWaiters(String producerName, long value) {
        // RS (all categories)
        for (ReservationStation rs : fpAddStations) {
            if (producerName.equals(rs.getQj())) {
                rs.setQj(null);
                rs.setVj(value);
            }
            if (producerName.equals(rs.getQk())) {
                rs.setQk(null);
                rs.setVk(value);
            }
        }
        for (ReservationStation rs : fpMulStations) {
            if (producerName.equals(rs.getQj())) {
                rs.setQj(null);
                rs.setVj(value);
            }
            if (producerName.equals(rs.getQk())) {
                rs.setQk(null);
                rs.setVk(value);
            }
        }
        for (ReservationStation rs : intAluStations) {
            if (producerName.equals(rs.getQj())) {
                rs.setQj(null);
                rs.setVj(value);
            }
            if (producerName.equals(rs.getQk())) {
                rs.setQk(null);
                rs.setVk(value);
            }
        }

        // Load buffers (for address dependencies)
     // Load buffers (for address dependencies)
        for (LoadBufferEntry lb : loadBuffers) {
            if (producerName.equals(lb.getAddressQ())) {
                lb.setAddressQ(null);
                // base register value + offset
                long base = registers.getInt(lb.getBaseRegIndex());
                lb.setAddress(base + lb.getOffset());
            }
        }

        // Store buffers (for address and value deps)
        for (StoreBufferEntry sb : storeBuffers) {
            if (producerName.equals(sb.getAddressQ())) {
                sb.setAddressQ(null);
                long base = registers.getInt(sb.getBaseRegIndex());
                sb.setAddress(base + sb.getOffset());
            }
            if (producerName.equals(sb.getValueQ())) {
                sb.setValueQ(null);
                sb.setValue(value);
            }
        }
    }
    
    private void startReadyExecutions() {
        // ---------- INT ALU: DADDI, DSUBI, BEQ, BNE ----------
        for (ReservationStation rs : intAluStations) {
            if (rs.isBusy()
                    && !rs.isExecuting()
                    && rs.isReady()) {

                rs.setExecutionLatency(intAluLatency);
                rs.setRemainingCycles(intAluLatency);

                Instruction instr = rs.getInstruction();
                if (instr != null && instr.getStartExecCycle() == -1) {
                    instr.setStartExecCycle(currentCycle);
                }
            }
        }

        // ---------- LOADS ----------
        for (LoadBufferEntry lb : loadBuffers) {
            if (!lb.isBusy()) continue;
            if (lb.isExecuting()) continue;
            if (!lb.isAddressReady()) continue;
            if (!canLoadExecute(lb)) continue; // respect older stores to same address

         // DEBUG:
            System.out.println("startReadyExecutions: starting LOAD exec on "
                    + lb.getName()
                    + " at cycle " + currentCycle
                    + " addr=" + lb.getAddress());
            // For now: use fixed load latency (later we use cache)
            lb.setRemainingCycles(loadLatencyBase);

            Instruction instr = lb.getInstruction();
            if (instr != null && instr.getStartExecCycle() == -1) {
                instr.setStartExecCycle(currentCycle);
            }

            // We can compute the value now from memory
//            InstructionType t = instr.getType();
//            boolean isD = isDouble(t);
//            long addr = lb.getAddress();
//            long val = isD ? memory.loadDouble(addr) : memory.loadWord(addr);
//            lb.setValue(val);
//            // DEBUG:
//            System.out.println("  load will read value=" + val + " into dest=" + lb.getDestReg());
        }

        // ---------- STORES ----------
        for (StoreBufferEntry sb : storeBuffers) {
            if (!sb.isBusy()) continue;
            if (sb.isExecuting()) continue;
            if (!sb.isAddressReady()) continue;
            if (!sb.isValueReady()) continue;

            sb.setRemainingCycles(storeLatencyBase);

            Instruction instr = sb.getInstruction();
            if (instr != null && instr.getStartExecCycle() == -1) {
                instr.setStartExecCycle(currentCycle);
            }
        }
    }

    
    private void issueInstruction() {
        if (pc >= program.size()) return;
        if (fetchStalled) return;

        Instruction instr = program.getInstruction(pc);
        InstructionType type = instr.getType();

        switch (type) {
            case DADDI:
            case DSUBI:
                if (issueIntAluImmediate(instr)) {
                    pc++; // normal sequential issue
                }
                break;

            case BEQ:
            case BNE:
                if (issueBranch(instr)) {
                    pc++;          // temporary next
                    fetchStalled = true; // stop issuing until branch resolved
                }
                break;
                
            case LW:
            case LD:
            case L_S:
            case L_D:
                if (issueLoad(instr)) {
                    pc++;
                }
                break;

            case SW:
            case SD:
            case S_S:
            case S_D:
                if (issueStore(instr)) {
                    pc++;
                }
                break;
                
            default:
                // other types not implemented yet
                break;
        }
    }

    private boolean issueIntAluImmediate(Instruction instr) {
        ReservationStation free = findFreeIntAluRS();
        if (free == null) return false; // cannot issue this cycle

        free.setBusy(true);
        free.setOp(instr.getType());
        free.setInstruction(instr);

        int rd = instr.getRd();
        int rsIdx = instr.getRs();
        long imm = instr.getImmediate();

        // source operand Rrs
        String owner = regStatus.getIntOwner(rsIdx);
        if (owner == null) {
            free.setQj(null);
            free.setVj(registers.getInt(rsIdx));
        } else {
            free.setQj(owner);
        }
        free.setQk(null);
        free.setVk(0);

        free.setA(imm);

        // destination Rrd
        String destReg = "R" + rd;
        free.setDest(destReg);
        if (rd != 0) { // ignore R0 ownership
            regStatus.setIntOwner(rd, free.getName());
        }

        // timing
        instr.setIssueCycle(currentCycle);

        return true;
    }

    private boolean issueBranch(Instruction instr) {
        ReservationStation free = findFreeIntAluRS();
        if (free == null) return false;

        free.setBusy(true);
        free.setOp(instr.getType());
        free.setInstruction(instr);

        int rsIdx = instr.getRs();
        int rtIdx = instr.getRt();
        long targetIndex = instr.getImmediate(); // we stored absolute PC index here in parser pass2

        // source rs
        String ownerRs = regStatus.getIntOwner(rsIdx);
        if (ownerRs == null) {
            free.setQj(null);
            free.setVj(registers.getInt(rsIdx));
        } else {
            free.setQj(ownerRs);
        }

        // source rt
        String ownerRt = regStatus.getIntOwner(rtIdx);
        if (ownerRt == null) {
            free.setQk(null);
            free.setVk(registers.getInt(rtIdx));
        } else {
            free.setQk(ownerRt);
        }

        free.setA(targetIndex); // branch target
        free.setDest(null);     // no destination register

        instr.setIssueCycle(currentCycle);

        return true;
    }

    private ReservationStation findFreeIntAluRS() {
        for (ReservationStation rs : intAluStations) {
            if (!rs.isBusy()) return rs;
        }
        return null;
    }
    
    private void advanceExecuting() {
        // Reservation stations
        for (ReservationStation rs : fpAddStations) {
            if (rs.isExecuting()) {
                int before = rs.getRemainingCycles();
                rs.setRemainingCycles(before - 1);
                if (before == 1 && rs.getInstruction() != null) {
                    rs.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }
        for (ReservationStation rs : fpMulStations) {
            if (rs.isExecuting()) {
                int before = rs.getRemainingCycles();
                rs.setRemainingCycles(before - 1);
                if (before == 1 && rs.getInstruction() != null) {
                    rs.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }
        for (ReservationStation rs : intAluStations) {
            if (rs.isExecuting()) {
                int before = rs.getRemainingCycles();
                rs.setRemainingCycles(before - 1);
                if (before == 1 && rs.getInstruction() != null) {
                    rs.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }

        // Load buffers
        for (LoadBufferEntry lb : loadBuffers) {
            if (lb.isExecuting()) {
                int before = lb.getRemainingCycles();
                lb.setRemainingCycles(before - 1);
                System.out.println("advanceExecuting: LOAD " + lb.getName()
                        + " before=" + before
                        + " after=" + lb.getRemainingCycles()
                        + " cycle=" + currentCycle);

                if (before == 1 && lb.getInstruction() != null) {
                    lb.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }

        // Store buffers
        for (StoreBufferEntry sb : storeBuffers) {
            if (sb.isExecuting()) {
                int before = sb.getRemainingCycles();
                sb.setRemainingCycles(before - 1);
                if (before == 1 && sb.getInstruction() != null) {
                    sb.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }
    }

    private CycleState takeSnapshot() {
        // Shallow copies for RS and buffers (ok for now)
        List<ReservationStation> fpAddCopy = new ArrayList<>(fpAddStations);
        List<ReservationStation> fpMulCopy = new ArrayList<>(fpMulStations);
        List<ReservationStation> intAluCopy = new ArrayList<>(intAluStations);
        List<LoadBufferEntry> loadCopy = new ArrayList<>(loadBuffers);
        List<StoreBufferEntry> storeCopy = new ArrayList<>(storeBuffers);

        long[] intRegsCopy = registers.getIntRegsCopy();
        long[] fpRegsCopy  = registers.getFpRegsCopy();

        CacheLine[][] cacheCopy = cache.getSets(); // later we can deep copy if needed

        List<Instruction> instrCopy = program.getInstructions(); // timing fields live in Instruction

        return new CycleState(
                currentCycle,
                pc,
                fpAddCopy,
                fpMulCopy,
                intAluCopy,
                loadCopy,
                storeCopy,
                intRegsCopy,
                fpRegsCopy,
                cacheCopy,
                instrCopy
        );
    }
    private boolean isLoad(InstructionType t) {
        return t == InstructionType.LW
            || t == InstructionType.LD
            || t == InstructionType.L_S
            || t == InstructionType.L_D;
    }

    private boolean isStore(InstructionType t) {
        return t == InstructionType.SW
            || t == InstructionType.SD
            || t == InstructionType.S_S
            || t == InstructionType.S_D;
    }

    private boolean isDouble(InstructionType t) {
        return t == InstructionType.LD
            || t == InstructionType.SD
            || t == InstructionType.L_D
            || t == InstructionType.S_D;
    }


    private boolean issueLoad(Instruction instr) {
    	System.out.println("issueLoad: pc=" + instr.getPcIndex()
                + " raw='" + instr.getRawText() + "'"
                + " memRegIsFp=" + instr.isMemRegFp()
                + " rd=" + instr.getRd());

    	LoadBufferEntry free = null;
        for (LoadBufferEntry lb : loadBuffers) {
            if (!lb.isBusy()) {
                free = lb;
                break;
            }
        }
        if (free == null) return false;

        free.setBusy(true);
        free.setInstruction(instr);

        // dest register (R or F based on parsed operand)
        int rd = instr.getRd();
        boolean isFp = instr.isMemRegFp();

        String destReg = (isFp ? "F" : "R") + rd;
        free.setDestReg(destReg);

        if (isFp) {
            regStatus.setFpOwner(rd, free.getName());
        } else {
            if (rd != 0) {
                regStatus.setIntOwner(rd, free.getName());
            }
        }

        // base register + offset
        int base = instr.getRs();
        long offset = instr.getImmediate();
        free.setBaseRegIndex(base);
        free.setOffset(offset);

        String owner = regStatus.getIntOwner(base);
        if (owner == null) {
            free.setAddressQ(null);
            long baseVal = registers.getInt(base);
            free.setAddress(baseVal + offset);
        } else {
            free.setAddressQ(owner);
        }

        instr.setIssueCycle(currentCycle);
        return true;
    }



    private boolean issueStore(Instruction instr) {
        StoreBufferEntry free = null;
        for (StoreBufferEntry sb : storeBuffers) {
            if (!sb.isBusy()) {
                free = sb;
                break;
            }
        }
        if (free == null) return false;

        free.setBusy(true);
        free.setInstruction(instr);

        // base + offset
        int base = instr.getRs();
        long offset = instr.getImmediate();
        free.setBaseRegIndex(base);
        free.setOffset(offset);

        String ownerBase = regStatus.getIntOwner(base);
        if (ownerBase == null) {
            free.setAddressQ(null);
            long baseVal = registers.getInt(base);
            free.setAddress(baseVal + offset);
        } else {
            free.setAddressQ(ownerBase);
        }

        // value to store comes from rd (R or F depending on original operand)
        int srcRegIndex = instr.getRd();
        boolean isFp = instr.isMemRegFp();

        if (isFp) {
            String ownerVal = regStatus.getFpOwner(srcRegIndex);
            if (ownerVal == null) {
                free.setValueQ(null);
                free.setValue(registers.getFp(srcRegIndex));
            } else {
                free.setValueQ(ownerVal);
            }
        } else {
            String ownerVal = regStatus.getIntOwner(srcRegIndex);
            if (ownerVal == null) {
                free.setValueQ(null);
                free.setValue(registers.getInt(srcRegIndex));
            } else {
                free.setValueQ(ownerVal);
            }
        }

        instr.setIssueCycle(currentCycle);
        return true;
    }


    private boolean canLoadExecute(LoadBufferEntry lb) {
        Instruction loadInstr = lb.getInstruction();
        if (loadInstr == null) return false;

        int loadPc = loadInstr.getPcIndex();

        for (StoreBufferEntry sb : storeBuffers) {
            if (!sb.isBusy()) continue;
            Instruction storeInstr = sb.getInstruction();
            if (storeInstr == null) continue;

            // If there is ANY older store still pending, don't let this load run yet
            if (storeInstr.getPcIndex() < loadPc) {
                return false;
            }
        }

        return true;
    }


}