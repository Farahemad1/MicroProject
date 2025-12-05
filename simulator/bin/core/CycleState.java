package core;

import java.util.List;

public class CycleState {

    private final int cycleNumber;
    private final int pc;

    private final List<ReservationStation> fpAddStations;
    private final List<ReservationStation> fpMulStations;
    private final List<ReservationStation> intAluStations;
    private final List<LoadBufferEntry> loadBuffers;
    private final List<StoreBufferEntry> storeBuffers;

    private final long[] intRegs;
    private final long[] fpRegs;

    private final CacheLine[][] cacheSnapshot;

    private final List<Instruction> instructionsWithTiming;

    public CycleState(
            int cycleNumber,
            int pc,
            List<ReservationStation> fpAddStations,
            List<ReservationStation> fpMulStations,
            List<ReservationStation> intAluStations,
            List<LoadBufferEntry> loadBuffers,
            List<StoreBufferEntry> storeBuffers,
            long[] intRegs,
            long[] fpRegs,
            CacheLine[][] cacheSnapshot,
            List<Instruction> instructionsWithTiming
    ) {
        this.cycleNumber = cycleNumber;
        this.pc = pc;
        this.fpAddStations = fpAddStations;
        this.fpMulStations = fpMulStations;
        this.intAluStations = intAluStations;
        this.loadBuffers = loadBuffers;
        this.storeBuffers = storeBuffers;
        this.intRegs = intRegs;
        this.fpRegs = fpRegs;
        this.cacheSnapshot = cacheSnapshot;
        this.instructionsWithTiming = instructionsWithTiming;
    }

    public int getCycleNumber() { return cycleNumber; }
    public int getPc() { return pc; }

    public List<ReservationStation> getFpAddStations() { return fpAddStations; }
    public List<ReservationStation> getFpMulStations() { return fpMulStations; }
    public List<ReservationStation> getIntAluStations() { return intAluStations; }
    public List<LoadBufferEntry> getLoadBuffers() { return loadBuffers; }
    public List<StoreBufferEntry> getStoreBuffers() { return storeBuffers; }

    public long[] getIntRegs() { return intRegs; }
    public long[] getFpRegs() { return fpRegs; }

    public CacheLine[][] getCacheSnapshot() { return cacheSnapshot; }

    public List<Instruction> getInstructionsWithTiming() {
        return instructionsWithTiming;
    }
}
