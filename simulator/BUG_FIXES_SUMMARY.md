# Tomasulo Simulator - Bug Fixes Summary

## Date: December 6, 2025

## Critical Issues Fixed

### Issue 1: Cache Hit/Miss Penalty Not Working Correctly ✅ FIXED

**Problem:** Cache penalties were not being applied correctly to load instructions' execution cycles.

**Root Cause:** The load execution was only using `loadLatencyBase` without considering cache hit/miss latency.

**Solution Implemented:**
- Modified `startReadyExecutions()` method in `TomasuloEngine.java` (lines ~682-710)
- Added cache latency check using `cache.probeLatency(addr, isD, false)` 
- Total load execution time is now: **cache_latency + load_latency**
  - Cache HIT: `hit_latency (1) + load_latency (2) = 3 cycles`
  - Cache MISS: `miss_penalty (10) + hit_latency (1) + load_latency (2) = 13 cycles`

**Verification:** Test with `test_cache.txt` shows:
- First load (addr 0): 13 cycles (MISS)
- Second load (addr 8, same block): 3 cycles (HIT)
- Third load (addr 100, different block): 13 cycles (MISS)
- Cache statistics: 1 hit, 2 misses ✓

---

### Issue 2: Loop/Branch Mechanism Not Working ✅ FIXED

**Problem:** Branch instructions (BEQ/BNE) were not resolving correctly, causing loops to fail.

**Root Causes Identified and Fixed:**

#### 2.1 Branch Resolution and PC Update
**Solution:**
- Enhanced `handleBranchWriteBack()` method (lines ~540-620)
- Added proper branch condition evaluation
- Implemented PC update to branch target when taken
- Added instruction flushing after taken branches
- Unstall fetch after branch resolution

#### 2.2 Instruction Queue Flushing
**Solution:**
- Implemented `flushInstructionsAfter()` method
- Clears all reservation stations, load buffers, and store buffers for instructions after the branch
- Resets register ownership for flushed instructions
- Prevents incorrectly fetched instructions from completing

#### 2.3 Critical: Reservation Station Premature Restart Bug
**Problem:** RS with finished execution but pending writeback were being restarted for new instructions.

**Solution:**
- Added check in `startReadyExecutions()` for FP ADD, FP MUL, and INT ALU stations
- Prevents restart if `instr.getEndExecCycle() != -1 && instr.getWriteBackCycle() == -1`
- This ensures RS waits for writeback before accepting new instructions

**Code Location:** Lines ~740-810 in TomasuloEngine.java

```java
// Don't restart if already finished execution but not yet written back
Instruction instr = rs.getInstruction();
if (instr != null && instr.getEndExecCycle() != -1 && instr.getWriteBackCycle() == -1) {
    continue; // waiting for writeback
}
```

---

## Additional Fixes

### Fix 3: Register File Encoding Issue ✅ FIXED
**Problem:** Unicode characters in RegisterFile.java comments caused compilation errors.
**Solution:** Changed `R0�R31` and `F0�F31` to `R0-R31` and `F0-F31`.

---

## Test Results

### Cache Test (`test_cache.txt`)
```
Load F1 from addr 0: 13 cycles (MISS)
Load F2 from addr 8: 3 cycles (HIT - same block)
Load F3 from addr 100: 13 cycles (MISS - different block)
Final: F1=100, F2=200, Cache hits=1, misses=2 ✓
```

### Integer ALU Test (`test_int.txt`)
```
DADDI R1, R0, 24   → R1 = 24 ✓
DADDI R2, R0, 10   → R2 = 10 ✓
DSUBI R1, R1, 8    → R1 = 16 ✓
```

### Loop Test (`test_loop.txt`)
```
Initial: R1=24, R2=0
Iteration 1: R1=16 (24-8) ✓
Branch taken, PC jumps to LOOP
Iteration 2: Continues correctly ✓
```

---

## Files Modified

1. **TomasuloEngine.java**
   - `startReadyExecutions()`: Added cache latency calculation for loads
   - `startReadyExecutions()`: Added checks to prevent premature RS restart
   - `handleBranchWriteBack()`: Enhanced branch resolution
   - `flushInstructionsAfter()`: New method for pipeline flushing
   - `handleIntAluWriteBack()`: Added debug logging
   - `handleLoadWriteBack()`: Added debug logging

2. **RegisterFile.java**
   - Fixed unicode character encoding in comments

3. **Test Files Created**
   - `test_cache.txt`: Cache hit/miss penalty verification
   - `test_loop.txt`: Loop with BNE test case
   - `test_int.txt`: Integer ALU operations test
   - `TestLoopEngine.java`: Enhanced test harness

---

## Key Implementation Details

### Cache Access Logic
- **Probe Phase:** `cache.probeLatency()` checks for hit/miss without modifying cache state
- **Execute Phase:** Load waits for `cacheLatency + loadLatency` cycles
- **Writeback Phase:** `cache.loadNoLatency()` performs actual memory access and updates cache

### Branch Execution Timeline
1. **Issue:** Branch enters INT ALU RS, fetch stalls
2. **Execute:** Wait for operands, evaluate condition
3. **Writeback:** 
   - Update PC (if taken)
   - Flush pipeline (if taken)
   - Resume fetch

### Loop Execution
- Labels are resolved to PC indices during parsing
- Branch target stored in RS.A field
- Loop continues while branch condition is true
- Proper handling of register updates across iterations

---

## Remaining Considerations

1. **Store Instructions:** Currently use write-through policy, don't check cache during execution (as per Tomasulo semantics)

2. **Branch Prediction:** Not implemented (as per requirements - no speculation)

3. **Register R0:** Properly handled as always zero in integer operations

4. **Debug Output:** Extensive logging added for troubleshooting, can be removed for production

---

## Compilation and Execution

```cmd
cd "c:\Users\farah\Desktop\MicroProject (2)\MicroProject\simulator"
javac -d bin\classes -cp src src\core\*.java
java -cp bin\classes core.TestEngine src\test_cache.txt
java -cp bin\classes core.TestEngine src\test_int.txt
java -cp bin\classes core.TestLoopEngine
```

---

## Conclusion

Both critical bugs have been successfully fixed:

✅ **Cache penalties** are now correctly applied (hit_latency + load_latency OR miss_penalty + hit_latency + load_latency)

✅ **Loops/branches** work correctly with proper PC updates, pipeline flushing, and RS management

The simulator now correctly implements the Tomasulo algorithm with cache modeling and branch handling according to the project requirements.
