package core;

public class TomasuloTimingTest {

    public static void main(String[] args) throws Exception {
        Parser p = new Parser();
        Program prog = p.parse(new java.io.File("src/test1.txt"));

        RegisterFile rf = new RegisterFile();
        RegisterStatus rs = new RegisterStatus();
        Memory mem = new Memory();
        Cache cache = new Cache(1024, 16, 2, 1, 10, mem);

        // latencies chosen to match DumpTimings usage
        int fpAddLat = 2;
        int fpMulLat = 4;
        int fpDivLat = 40;
        int intAluLat = 1;
        int loadBase = 2;
        int storeBase = 2;

        TomasuloEngine engine = new TomasuloEngine(
                prog, rf, rs, mem, cache,
                3, 2, 3, 3, 3,
                fpAddLat, fpMulLat, fpDivLat, intAluLat, loadBase, storeBase
        );

        // run sufficient cycles
        for (int i = 0; i < 200; i++) engine.nextCycle();

        boolean ok = true;
        StringBuilder errors = new StringBuilder();

        for (Instruction instr : prog.getInstructions()) {
            int s = instr.getStartExecCycle();
            int e = instr.getEndExecCycle();
            int w = instr.getWriteBackCycle();

            if (s <= 0) {
                ok = false;
                errors.append("Instr " + instr.getPcIndex() + " has invalid start=" + s + "\n");
                continue;
            }

            int expectedLatency = latencyFor(instr.getType(), fpAddLat, fpMulLat, fpDivLat, intAluLat, loadBase, storeBase);
            int expectedEnd = s + expectedLatency - 1;
            int expectedW = expectedEnd + 1;

            if (e != expectedEnd) {
                ok = false;
                errors.append(String.format("Instr %d: type=%s start=%d expectedEnd=%d got=%d\n",
                        instr.getPcIndex(), instr.getType(), s, expectedEnd, e));
            }
            // CDB serialization may delay the actual write result when multiple
            // producers complete around the same time. We require that the
            // write result happens no earlier than (end + 1) and as soon as the
            // single CDB permits (w >= expectedW).
            // In strict lecture semantics we expect the write to occur exactly one
            // cycle after execution completes.
            if (w != expectedW) {
                ok = false;
                errors.append(String.format("Instr %d: type=%s end=%d expectedW=%d got=%d\n",
                        instr.getPcIndex(), instr.getType(), e, expectedW, w));
            }
        }

        if (ok) {
            System.out.println("TomasuloTimingTest PASSED");
            System.exit(0);
        } else {
            System.err.println("TomasuloTimingTest FAILED:\n" + errors.toString());
            System.exit(2);
        }
    }

    private static int latencyFor(InstructionType t, int fpAddLat, int fpMulLat, int fpDivLat, int intAluLat, int loadBase, int storeBase) {
        switch (t) {
            case ADD_S: case ADD_D: case SUB_S: case SUB_D:
                return fpAddLat;
            case MUL_S: case MUL_D:
                return fpMulLat;
            case DIV_S: case DIV_D:
                return fpDivLat;
            case DADDI: case DSUBI:
                return intAluLat;
            case LW: case LD: case L_S: case L_D:
                return loadBase; // lecture-mode uses base only
            case SW: case SD: case S_S: case S_D:
                return storeBase;
            default:
                return 1;
        }
    }
}
