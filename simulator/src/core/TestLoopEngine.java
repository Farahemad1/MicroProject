package core;

import java.io.*;
public class TestLoopEngine {
    public static void main(String[] args) throws Exception {
        Parser p = new Parser();
        String progPath = "src/test_loop.txt";
        Program prog = p.parse(new File(progPath));

        RegisterFile rf = new RegisterFile();
        RegisterStatus rs = new RegisterStatus();
        Memory mem = new Memory();
        
        // Initialize memory with test data
        mem.storeDouble(0, 100);
        mem.storeDouble(8, 200);
        mem.storeDouble(16, 300);
        mem.storeDouble(24, 400);
        mem.storeDouble(32, 500);
        mem.storeDouble(44, 600);
        
        System.out.println("=== Memory Initialization ===");
        System.out.println("mem[0] = " + mem.loadDouble(0));
        System.out.println("mem[8] = " + mem.loadDouble(8));
        System.out.println();
        
        Cache cache = new Cache(1024, 16, 2, 1, 10, mem);

        TomasuloEngine engine = new TomasuloEngine(
                prog, rf, rs, mem, cache,
                3, 2, 3, 3, 3,
            2,  // fpAddLatency
            4,  // fpMulLatency
            40, // fpDivLatency
            1,  // intAluLatency
            2,  // loadLatencyBase
            2   // storeLatencyBase
        );

        for (int i = 0; i < 50; i++) {
            System.out.println("\n===== Cycle " + engine.getCurrentCycle()
                    + " PC=" + engine.getPc()
                    + " R1=" + rf.getInt(1)
                    + " R2=" + rf.getInt(2)
                    + " F0=" + rf.getFp(0)
                    + " F2=" + rf.getFp(2)
                    + " F4=" + rf.getFp(4) + " =====");
            engine.nextCycle();
        }

        System.out.println("\n=== Final State ===");
        System.out.println("R1 = " + rf.getInt(1));
        System.out.println("R2 = " + rf.getInt(2));
        System.out.println("F0 = " + rf.getFp(0));
        System.out.println("F2 = " + rf.getFp(2));
        System.out.println("F4 = " + rf.getFp(4));
    }
}
