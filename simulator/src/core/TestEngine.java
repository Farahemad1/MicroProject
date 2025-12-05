package core;

import java.io.*;
public class TestEngine {
    public static void main(String[] args) throws Exception {
        Parser p = new Parser();
        // Use a CLI arg if provided, otherwise default to the project's `src/test1.txt`
        String progPath = (args != null && args.length > 0) ? args[0] : "src/test1.txt";
        Program prog = p.parse(new File(progPath));

        RegisterFile rf = new RegisterFile();
        RegisterStatus rs = new RegisterStatus();
        Memory mem = new Memory();
        Cache cache = new Cache(1024, 16, 2, 1, 10, mem);

        TomasuloEngine engine = new TomasuloEngine(
                prog, rf, rs, mem, cache,
                3, 2, 3, 3, 3,
                2,  // fpAddLatency (unused)
                4,  // fpMulLatency (unused)
                1,  // intAluLatency
                2,  // loadLatencyBase
                2   // storeLatencyBase
        );

        for (int i = 0; i < 20; i++) {
            System.out.println("Cycle " + engine.getCurrentCycle()
                    + " PC=" + engine.getPc()
                    + " R1=" + rf.getInt(1)
                    + " R2=" + rf.getInt(2)
                    + " F0=" + rf.getFp(0));
            engine.nextCycle();
        }

        System.out.println("Final mem[8] dbl = " + mem.loadDouble(8));
    }
}