package core;

import java.util.ArrayList;
import java.util.List;

/**
 * Tomasulo's Algorithm Implementation with User-Configurable Parameters.
 * 
 * ============================================================================
 * USER-CONFIGURABLE INSTRUCTION LATENCIES:
 * ============================================================================
 * Before starting simulation, users can configure latencies for each instruction type:
 * 
 * FLOATING-POINT INSTRUCTIONS:
 *   - FP Add/Sub Latency: ADD.S, ADD.D, SUB.S, SUB.D
 *   - FP Mul Latency: MUL.S, MUL.D
 *   - FP Div Latency: DIV.S, DIV.D
 * 
 * INTEGER INSTRUCTIONS:
 *   - Int ALU Latency: DADDI, DSUBI, BEQ, BNE
 * 
 * MEMORY INSTRUCTIONS:
 *   - Load Base Latency: L.D, L.S, LD, LW
 *   - Store Base Latency: S.D, S.S, SD, SW
 * 
 * ============================================================================
 * RESERVATION STATION CONFIGURATION:
 * ============================================================================
 * Users can specify the number of reservation stations for each type:
 * 
 *   - FP Add RS: For ADD.S, ADD.D, SUB.S, SUB.D
 *   - FP Mul RS: For MUL.S, MUL.D, DIV.S, DIV.D
 *   - Int ALU RS: For DADDI, DSUBI, BEQ, BNE (integer instructions)
 *   - Load Buffers: For all load instructions
 *   - Store Buffers: For all store instructions
 * 
 * NOTE: ALL instructions (integer and floating-point) enter the architecture
 *       and use reservation stations. This differs from the lecture where
 *       only FP instructions were considered.
 * 
 * ============================================================================
 * CACHE CONFIGURATION:
 * ============================================================================
 * Cache parameters are user-configurable (see Cache.java for details):
 * 
 *   - Cache Size (bytes)
 *   - Block Size (bytes)
 *   - Associativity (ways per set)
 *   - Hit Latency (cycles)
 *   - Miss Penalty (cycles)
 * 
 * IMPORTANT: Cache misses apply ONLY to DATA accesses (loads/stores).
 *            Instruction fetches are NOT cached and have no miss penalty.
 * 
 * ============================================================================
 * ADDRESS CLASH HANDLING:
 * ============================================================================
 * The engine correctly handles memory address dependencies:
 * 
 *   - Loads wait for older stores with unknown addresses
 *   - Loads wait for older stores to same address to complete
 *   - Stores commit in program order
 *   - See canLoadExecute() for disambiguation logic
 * 
 * ============================================================================
 */
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
    private final int fpDivLatency;
    private final int intAluLatency;
    private final int loadLatencyBase;  // we�ll combine with cache latency
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
            int fpDivLatency,
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
        this.fpDivLatency = fpDivLatency;
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

    // Expose register status for GUI
    public RegisterStatus getRegisterStatus() {
        return regStatus;
    }

    public void nextCycle() {
        // Advance global cycle counter
        currentCycle++;

        // Stage 1: Write Result (CDB broadcast from previous cycle's completed executions)
        // 1) Commit any finished stores (stores don't use CDB but must write to memory
        //    before next cycle's operations)
        completeFinishedStores();

        // 2) Do one CDB broadcast this cycle (for instructions that completed execution in previous cycles)
        //    Values broadcast here will be available for execution start THIS cycle (after broadcast)
        //    This also FREES the RS/LB that broadcasts
        handleWriteBack();

        // Stage 2: Execute
        // 3) Start execution for any RS / loads / stores that are now ready
        //    (values from THIS cycle's CDB broadcast, or from registers)
        startReadyExecutions();

        // 4) Decrement remainingCycles for all executing RS / loads / stores
        //    and mark execute-complete when remaining reaches 0 (this cycle)
        advanceExecuting();

        // Stage 3: Issue
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
        // We don�t restore full internal state yet � we will later
    }
    
    private void completeFinishedStores() {
        for (StoreBufferEntry sb : storeBuffers) {
            if (!sb.isBusy()) continue;
            // Only commit stores that actually finished execution this cycle
            Instruction sbInstr = sb.getInstruction();
            if (sbInstr == null) continue;
            // commit only stores that finished execution in a previous cycle
            if (sbInstr.getEndExecCycle() == -1 || sbInstr.getEndExecCycle() >= currentCycle) continue;

            Instruction instr = sbInstr;
            if (instr.getWriteBackCycle() != -1) continue; // already committed

            // Perform the actual memory write via cache (write-through/no-allocate)
            InstructionType t = instr.getType();
            boolean isD = isDouble(t);
            long addr = sb.getAddress();
            long val = sb.getValue();

            cache.storeNoLatency(addr, val, isD);

            // Mark WB cycle for timing table
            instr.setWriteBackCycle(currentCycle);

            // Free this store buffer entry
            sb.clear();
        }
    }

    
    private void handleWriteBack() {
        // ---- 1) Collect RS candidates (finished this cycle, not yet WB) ----
        List<ReservationStation> rsCandidates = new ArrayList<>();
        // FP add
        for (ReservationStation rs : fpAddStations) {
                if (rs.isBusy()
                    && rs.getOp() != null
                    && rs.getInstruction() != null
                    && rs.getInstruction().getWriteBackCycle() == -1
                    // only consider producers that completed execution in a previous cycle
                    && rs.getInstruction().getEndExecCycle() != -1
                    && rs.getInstruction().getEndExecCycle() < currentCycle) {
                rsCandidates.add(rs);
            }
        }
        // FP mul
        for (ReservationStation rs : fpMulStations) {
                if (rs.isBusy()
                    && rs.getOp() != null
                    && rs.getInstruction() != null
                    && rs.getInstruction().getWriteBackCycle() == -1
                    && rs.getInstruction().getEndExecCycle() != -1
                    && rs.getInstruction().getEndExecCycle() < currentCycle) {
                rsCandidates.add(rs);
            }
        }
        // INT ALU
        for (ReservationStation rs : intAluStations) {
                if (rs.isBusy()
                    && rs.getOp() != null
                    && rs.getInstruction() != null
                    && rs.getInstruction().getWriteBackCycle() == -1
                    && rs.getInstruction().getEndExecCycle() != -1
                    && rs.getInstruction().getEndExecCycle() < currentCycle) {
                rsCandidates.add(rs);
            }
        }

        // ---- 2) Collect LOAD buffer candidates (finished this cycle, not yet WB) ----
        List<LoadBufferEntry> loadCandidates = new ArrayList<>();
        for (LoadBufferEntry lb : loadBuffers) {
            if (lb.isBusy()
                    && lb.getInstruction() != null
                    && lb.getInstruction().getWriteBackCycle() == -1
                    // only loads that finished execution in an earlier cycle
                    && lb.getInstruction().getEndExecCycle() != -1
                    && lb.getInstruction().getEndExecCycle() < currentCycle) {
                loadCandidates.add(lb);
            }
        }

        // If nothing finished this cycle, nothing to broadcast
        if (rsCandidates.isEmpty() && loadCandidates.isEmpty()) {
            return;
        }

        // ---- 3) Choose the best producer for the single CDB this cycle ----
        String bestName = null;
        Instruction bestInstr = null;

        boolean bestIsRS = false;
        ReservationStation bestRS = null;
        LoadBufferEntry bestLB = null;

        int bestScore = -1;
        int bestStartCycle = Integer.MAX_VALUE;

        // 3a) Check RS producers
        for (ReservationStation rs : rsCandidates) {
            String name = rs.getName();
            Instruction instr = rs.getInstruction();
            int score = countDependents(name);           // how many wait on this producer
            int start = instr.getStartExecCycle();       // tie-breaker: earliest start
            if (start == -1) start = instr.getIssueCycle(); // fallback

            if (score > bestScore || (score == bestScore && start < bestStartCycle)) {
                bestScore = score;
                bestStartCycle = start;
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
            int start = instr.getStartExecCycle();
            if (start == -1) start = instr.getIssueCycle();

            if (score > bestScore || (score == bestScore && start < bestStartCycle)) {
                bestScore = score;
                bestStartCycle = start;
                bestName = name;
                bestInstr = instr;
                bestIsRS = false;
                bestRS = null;
                bestLB = lb;
            }
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
            } else if (bestRS.getCategory() == RSCategory.INT_ALU) {
                // Integer ALU result
                handleIntAluWriteBack(bestRS, bestInstr);
            } else {
                // FP add/mul result
                handleFpWriteBack(bestRS, bestInstr);
            }

            bestInstr.setWriteBackCycle(currentCycle);
            bestRS.clear();
        } else {
            // Load result: write loaded value to dest reg + broadcast
            handleLoadWriteBack(bestLB, bestInstr);

            bestInstr.setWriteBackCycle(currentCycle);
            bestLB.clear();
        }
    }

    private void handleFpWriteBack(ReservationStation rs, Instruction instr) {
        long result = 0;
        InstructionType op = rs.getOp();

        switch (op) {
            case ADD_S: case ADD_D:
                result = rs.getVj() + rs.getVk();
                break;
            case SUB_S: case SUB_D:
                result = rs.getVj() - rs.getVk();
                break;
            case MUL_S: case MUL_D:
                result = rs.getVj() * rs.getVk();
                break;
            case DIV_S: case DIV_D:
                // simple integer division semantics for FP (per project notes)
                if (rs.getVk() == 0) result = 0; else result = rs.getVj() / rs.getVk();
                break;
            default:
                // unhandled
                break;
        }

        // 1) Write back to destination FP register if still owned by this RS
        String dest = rs.getDest();
        if (dest != null && dest.startsWith("F")) {
            int idx = Integer.parseInt(dest.substring(1));
            if (regStatus.getFpOwner(idx) != null && regStatus.getFpOwner(idx).equals(rs.getName())) {
                registers.setFp(idx, result);
                regStatus.setFpOwner(idx, null);
            }
        }

        // 2) Broadcast to waiting RS / buffers
        broadcastToWaiters(rs.getName(), result);
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
        // use cache to update state and obtain the value (latency already accounted for)
        long result = cache.loadNoLatency(addr, isD);

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
        // Build a set of end cycles already reserved by currently executing units.
        // For an executing unit with remainingCycles R, its predicted end cycle
        // (before this cycle's decrement) is currentCycle + R - 1.
        java.util.Set<Integer> reservedEnds = new java.util.HashSet<>();
        for (ReservationStation rs : fpAddStations) {
            if (rs.isExecuting()) {
                int predictedEnd = currentCycle + rs.getRemainingCycles() - 1;
                reservedEnds.add(predictedEnd);
            }
        }
        for (ReservationStation rs : fpMulStations) {
            if (rs.isExecuting()) {
                int predictedEnd = currentCycle + rs.getRemainingCycles() - 1;
                reservedEnds.add(predictedEnd);
            }
        }
        for (ReservationStation rs : intAluStations) {
            if (rs.isExecuting()) {
                int predictedEnd = currentCycle + rs.getRemainingCycles() - 1;
                reservedEnds.add(predictedEnd);
            }
        }
        for (LoadBufferEntry lb : loadBuffers) {
            if (lb.isExecuting()) {
                int predictedEnd = currentCycle + lb.getRemainingCycles() - 1;
                reservedEnds.add(predictedEnd);
            }
        }
        for (StoreBufferEntry sb : storeBuffers) {
            if (sb.isExecuting()) {
                int predictedEnd = currentCycle + sb.getRemainingCycles() - 1;
                reservedEnds.add(predictedEnd);
            }
        }

        // ---------- FP ADD/SUB ----------
        for (ReservationStation rs : fpAddStations) {
            if (rs.isBusy() && !rs.isExecuting() && rs.isReady()) {
                int lat = fpAddLatency;
                int intendedEnd = currentCycle + lat - 1;
                if (reservedEnds.contains(intendedEnd)) {
                    // Skip starting this RS now to avoid end-cycle collision
                    continue;
                }
                // Reserve the end cycle and start execution
                reservedEnds.add(intendedEnd);
                rs.setExecutionLatency(lat);
                rs.setRemainingCycles(lat);

                Instruction instr = rs.getInstruction();
                if (instr != null && instr.getStartExecCycle() == -1) {
                    instr.setStartExecCycle(currentCycle);
                }
            }
        }

        // ---------- FP MUL/DIV ----------
        for (ReservationStation rs : fpMulStations) {
            if (rs.isBusy() && !rs.isExecuting() && rs.isReady()) {
                InstructionType op = rs.getOp();
                int lat = fpMulLatency;
                if (op == InstructionType.DIV_S || op == InstructionType.DIV_D) lat = fpDivLatency;
                int intendedEnd = currentCycle + lat - 1;
                if (reservedEnds.contains(intendedEnd)) {
                    continue;
                }
                reservedEnds.add(intendedEnd);
                rs.setExecutionLatency(lat);
                rs.setRemainingCycles(lat);

                Instruction instr = rs.getInstruction();
                if (instr != null && instr.getStartExecCycle() == -1) {
                    instr.setStartExecCycle(currentCycle);
                }
            }
        }

        // ---------- INT ALU: DADDI, DSUBI, BEQ, BNE ----------
        for (ReservationStation rs : intAluStations) {
            if (rs.isBusy() && !rs.isExecuting() && rs.isReady()) {
                int lat = intAluLatency;
                int intendedEnd = currentCycle + lat - 1;
                if (reservedEnds.contains(intendedEnd)) {
                    continue;
                }
                reservedEnds.add(intendedEnd);
                rs.setExecutionLatency(lat);
                rs.setRemainingCycles(lat);

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

            // Determine intended end and avoid collisions
            Instruction instr = lb.getInstruction();
            InstructionType t = instr == null ? null : instr.getType();
            boolean isD = isDouble(t);
            long addr = lb.getAddress();
            
            // Cache-aware timing: probe if this address will hit or miss
            // On HIT: use base latency (cache provides data)
            // On MISS: add miss penalty to base latency (need to fetch from memory)
            int cachePenalty = cache.probeMissPenalty(addr, isD, false);
            int lat = loadLatencyBase + cachePenalty;
            
            int intendedEnd = currentCycle + lat - 1;
            if (reservedEnds.contains(intendedEnd)) {
                continue; // postpone starting this load this cycle
            }
            reservedEnds.add(intendedEnd);
            lb.setRemainingCycles(lat);

            if (instr != null && instr.getStartExecCycle() == -1) {
                instr.setStartExecCycle(currentCycle);
            }
        }

        // ---------- STORES ----------
        for (StoreBufferEntry sb : storeBuffers) {
            if (!sb.isBusy()) continue;
            if (sb.isExecuting()) continue;
            if (!sb.isAddressReady()) continue;
            if (!sb.isValueReady()) continue;

            Instruction instr = sb.getInstruction();
            InstructionType t = instr == null ? null : instr.getType();
            boolean isD = isDouble(t);
            long addr = sb.getAddress();
            
            // Cache-aware timing: probe if this address will hit or miss
            // Write-through + no-write-allocate: stores don't fetch on miss
            int cachePenalty = cache.probeMissPenalty(addr, isD, true);
            int lat = storeLatencyBase + cachePenalty;
            
            int intendedEnd = currentCycle + lat - 1;
            if (reservedEnds.contains(intendedEnd)) {
                continue;
            }
            reservedEnds.add(intendedEnd);
            sb.setRemainingCycles(lat);

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

            // FP add/sub
            case ADD_S: case ADD_D:
            case SUB_S: case SUB_D:
                if (issueFpOp(instr)) {
                    pc++;
                }
                break;

            // FP mul/div
            case MUL_S: case MUL_D:
            case DIV_S: case DIV_D:
                if (issueFpOp(instr)) {
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

        // source operand Rrs - follow rule: never have both V and Q filled
        String owner = regStatus.getIntOwner(rsIdx);
        if (owner == null) {
            free.setQj(null);
            free.setVj(registers.getInt(rsIdx));
        } else {
            free.setQj(owner);
            free.setVj(0); // Vj ignored when Qj is set
        }
        // Immediate goes in Vk (always ready)
        free.setQk(null);
        free.setVk(imm);

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

        // source rs - follow rule: never have both V and Q filled
        String ownerRs = regStatus.getIntOwner(rsIdx);
        if (ownerRs == null) {
            free.setQj(null);
            free.setVj(registers.getInt(rsIdx));
        } else {
            free.setQj(ownerRs);
            free.setVj(0); // Vj ignored when Qj is set
        }

        // source rt - follow rule: never have both V and Q filled
        String ownerRt = regStatus.getIntOwner(rtIdx);
        if (ownerRt == null) {
            free.setQk(null);
            free.setVk(registers.getInt(rtIdx));
        } else {
            free.setQk(ownerRt);
            free.setVk(0); // Vk ignored when Qk is set
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
                    // Execution completes in this cycle when remaining cycles reached 0
                    rs.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }
        for (ReservationStation rs : fpMulStations) {
            if (rs.isExecuting()) {
                int before = rs.getRemainingCycles();
                rs.setRemainingCycles(before - 1);
                if (before == 1 && rs.getInstruction() != null) {
                    // Execution completes in this cycle when remaining cycles reached 0
                    rs.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }
        for (ReservationStation rs : intAluStations) {
            if (rs.isExecuting()) {
                int before = rs.getRemainingCycles();
                rs.setRemainingCycles(before - 1);
                if (before == 1 && rs.getInstruction() != null) {
                    // Execution completes in this cycle when remaining cycles reached 0
                    rs.getInstruction().setEndExecCycle(currentCycle);
                }
            }
        }

        // Load buffers
        for (LoadBufferEntry lb : loadBuffers) {
            if (lb.isExecuting()) {
                int before = lb.getRemainingCycles();
                lb.setRemainingCycles(before - 1);

                if (before == 1 && lb.getInstruction() != null) {
                    // For loads, execution completes in this cycle
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
                    // For stores, execution completes in this cycle
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


    private boolean issueFpOp(Instruction instr) {
        InstructionType t = instr.getType();
        boolean isMulDiv = (t == InstructionType.MUL_S || t == InstructionType.MUL_D || t == InstructionType.DIV_S || t == InstructionType.DIV_D);

        ReservationStation free = null;
        if (isMulDiv) {
            for (ReservationStation rs : fpMulStations) if (!rs.isBusy()) { free = rs; break; }
        } else {
            for (ReservationStation rs : fpAddStations) if (!rs.isBusy()) { free = rs; break; }
        }
        if (free == null) return false;

        free.setBusy(true);
        free.setOp(instr.getType());
        free.setInstruction(instr);

        int rd = instr.getRd();
        int rsIdx = instr.getRs();
        int rtIdx = instr.getRt();

        // sources are FP regs - follow rule: never have both V and Q filled
        String ownerJ = regStatus.getFpOwner(rsIdx);
        if (ownerJ == null) { 
            free.setQj(null); 
            free.setVj(registers.getFp(rsIdx)); 
        } else { 
            free.setQj(ownerJ); 
            free.setVj(0); // Vj ignored when Qj is set
        }

        String ownerK = regStatus.getFpOwner(rtIdx);
        if (ownerK == null) { 
            free.setQk(null); 
            free.setVk(registers.getFp(rtIdx)); 
        } else { 
            free.setQk(ownerK); 
            free.setVk(0); // Vk ignored when Qk is set
        }

        // dest
        String dest = "F" + rd;
        free.setDest(dest);
        regStatus.setFpOwner(rd, free.getName());

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


    /**
     * ADDRESS CLASH HANDLING IN TOMASULO:
     * 
     * Determines if a load can safely execute without violating memory consistency.
     * This implements address-based disambiguation to handle:
     * 
     * 1. RAW (Read-After-Write) hazards:
     *    - Load must wait for older stores to same address to complete
     * 
     * 2. Unknown Store Addresses:
     *    - If an older store's address is unknown, load must wait conservatively
     *    - Prevents incorrect data being loaded
     * 
     * 3. Different Addresses:
     *    - If addresses differ, load can proceed (no hazard)
     * 
     * This ensures correct Tomasulo execution with memory disambiguation.
     * 
     * @param lb The load buffer entry to check
     * @return true if load can execute, false if must wait
     */
    private boolean canLoadExecute(LoadBufferEntry lb) {
        Instruction loadInstr = lb.getInstruction();
        if (loadInstr == null) return false;

        int loadPc = loadInstr.getPcIndex();

        // Check all store buffers for address conflicts
        for (StoreBufferEntry sb : storeBuffers) {
            if (!sb.isBusy()) continue;
            Instruction storeInstr = sb.getInstruction();
            if (storeInstr == null) continue;

            // Only check stores that are older (earlier in program order)
            if (storeInstr.getPcIndex() < loadPc) {
                // ADDRESS CLASH DETECTION:
                if (!sb.isAddressReady()) {
                    // Conservative: block load if older store address unknown
                    return false;
                } else {
                    long storeAddr = sb.getAddress();
                    if (storeAddr == lb.getAddress()) {
                        // ADDRESS CLASH: same address, load must wait
                        return false;
                    } else {
                        // Different address: no hazard, continue checking
                        continue;
                    }
                }
            }
        }

        // No address clashes detected, load can execute
        return true;
    }


}