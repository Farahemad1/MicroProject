package core;

import java.io.File;

public class DumpTimings {
    public static void main(String[] args) throws Exception {
        Parser p = new Parser();
        String progPath = (args != null && args.length > 0) ? args[0] : "src/test1.txt";
        Program prog = p.parse(new File(progPath));

        RegisterFile rf = new RegisterFile();
        RegisterStatus rs = new RegisterStatus();
        Memory mem = new Memory();
        Cache cache = new Cache(1024, 16, 2, 1, 10, mem);

        TomasuloEngine engine = new TomasuloEngine(
                prog, rf, rs, mem, cache,
                3, 2, 3, 3, 3,
            2, 4, 40, 1, 2, 2
        );

        // run until a max cycle or until no more progress
        int maxCycles = 100;
        for (int i = 0; i < maxCycles; i++) {
            engine.nextCycle();
        }

        System.out.println("Instruction timing table:");
        System.out.println("Idx | Text | I | S | E | W");
        for (Instruction instr : prog.getInstructions()) {
            System.out.printf("%2d | %s | %d | %d | %d | %d\n",
                    instr.getPcIndex(), instr.getRawText(), instr.getIssueCycle(), instr.getStartExecCycle(), instr.getEndExecCycle(), instr.getWriteBackCycle());
        }

        System.out.println("Final memory[8] dbl = " + mem.loadDouble(8));
    }
}
