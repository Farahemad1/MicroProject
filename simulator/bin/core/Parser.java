package core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Parser {

    public Program parse(File file) throws IOException {
        List<Instruction> instructions = new ArrayList<>();
        Map<String, Integer> labelMap = new HashMap<>();

        // ---------- FIRST PASS: build instructions and labels ----------
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int pcIndex = 0;

            while ((line = br.readLine()) != null) {
                String cleaned = stripComments(line).trim();
                if (cleaned.isEmpty()) continue;

                // handle label at start: LABEL: instruction...
                String label = null;
                int colonIndex = cleaned.indexOf(':');
                if (colonIndex != -1) {
                    label = cleaned.substring(0, colonIndex).trim();
                    cleaned = cleaned.substring(colonIndex + 1).trim();
                    if (!label.isEmpty()) {
                        labelMap.put(label, pcIndex);
                    }
                }

                if (cleaned.isEmpty()) {
                    // line had only a label
                    continue;
                }

                Instruction instr = parseInstructionLine(cleaned, pcIndex);
                instr.setLabel(label);
                instructions.add(instr);
                pcIndex++;
            }
        }

        // ---------- SECOND PASS: resolve branch labels ----------
        for (Instruction instr : instructions) {
            if (instr.getBranchLabel() != null) {
                Integer target = labelMap.get(instr.getBranchLabel());
                if (target == null) {
                    throw new IllegalArgumentException(
                        "Unknown label: " + instr.getBranchLabel() +
                        " in instruction: " + instr.getRawText()
                    );
                }
                instr.setImmediate(target); // store absolute target index
            }
        }

        return new Program(instructions, labelMap);
    }

    private String stripComments(String line) {
        int idx = line.indexOf('#');
        if (idx != -1) return line.substring(0, idx);
        idx = line.indexOf("//");
        if (idx != -1) return line.substring(0, idx);
        return line;
    }

    private Instruction parseInstructionLine(String line, int pcIndex) {
        // normalize spaces, but keep case
        String noExtraSpaces = line.replaceAll("\\s+", " ");

        // opcode is up to first space or end
        int spaceIdx = noExtraSpaces.indexOf(' ');
        String op = (spaceIdx == -1)
                ? noExtraSpaces
                : noExtraSpaces.substring(0, spaceIdx);

        String args = (spaceIdx == -1)
                ? ""
                : noExtraSpaces.substring(spaceIdx + 1).trim();

        InstructionType type = mapMnemonic(op);
        Instruction instr = new Instruction(type, line, pcIndex);

        switch (type) {
            case DADDI:
            case DSUBI:
                parseRRI(args, instr, true);  // rd, rs, imm
                break;

            case ADD_S: case ADD_D:
            case SUB_S: case SUB_D:
            case MUL_S: case MUL_D:
            case DIV_S: case DIV_D:
                parseRRR(args, instr);        // fd, fs, ft
                break;

            case LW: case SW:
            case LD: case SD:
            case L_S: case L_D:
            case S_S: case S_D:
                parseMemory(args, instr);     // R, offset(base)
                break;

            case BNE:
            case BEQ:
                parseBranch(args, instr);
                break;

            default:
                throw new IllegalArgumentException("Unhandled type " + type);
        }

        return instr;
    }

    private InstructionType mapMnemonic(String op) {
        // op is case-sensitive as requested
        switch (op) {
            case "DADDI": return InstructionType.DADDI;
            case "DSUBI": return InstructionType.DSUBI;

            case "ADD.S": return InstructionType.ADD_S;
            case "ADD.D": return InstructionType.ADD_D;
            case "SUB.S": return InstructionType.SUB_S;
            case "SUB.D": return InstructionType.SUB_D;
            case "MUL.S": return InstructionType.MUL_S;
            case "MUL.D": return InstructionType.MUL_D;
            case "DIV.S": return InstructionType.DIV_S;
            case "DIV.D": return InstructionType.DIV_D;

            case "LW": return InstructionType.LW;
            case "SW": return InstructionType.SW;
            case "LD": return InstructionType.LD;
            case "SD": return InstructionType.SD;
            case "L.S": return InstructionType.L_S;
            case "L.D": return InstructionType.L_D;
            case "S.S": return InstructionType.S_S;
            case "S.D": return InstructionType.S_D;

            case "BNE": return InstructionType.BNE;
            case "BEQ": return InstructionType.BEQ;
        }
        throw new IllegalArgumentException("Unknown opcode: " + op);
    }

    // rd, rs, imm   e.g. DADDI R1, R2, 8
    private void parseRRI(String args, Instruction instr, boolean immediateSigned) {
        String[] parts = args.split(",");
        if (parts.length != 3)
            throw new IllegalArgumentException("Expected rd, rs, imm: " + args);

        int rd = parseRegister(parts[0].trim());
        int rs = parseRegister(parts[1].trim());
        long imm = Long.parseLong(parts[2].trim());

        instr.setRd(rd);
        instr.setRs(rs);
        instr.setImmediate(imm);
    }

    // fd, fs, ft   e.g. ADD.D F0, F2, F4
    private void parseRRR(String args, Instruction instr) {
        String[] parts = args.split(",");
        if (parts.length != 3)
            throw new IllegalArgumentException("Expected rd, rs, rt: " + args);

        int rd = parseFpRegister(parts[0].trim());
        int rs = parseFpRegister(parts[1].trim());
        int rt = parseFpRegister(parts[2].trim());

        instr.setRd(rd);
        instr.setRs(rs);
        instr.setRt(rt);
    }

    // R, offset(Rbase)  e.g. L.D F6, 8(R2)  or  SW R1, 16(R3)
    private void parseMemory(String args, Instruction instr) {
        String[] parts = args.split(",");
        if (parts.length != 2)
            throw new IllegalArgumentException("Expected reg, offset(base): " + args);

        String regPart = parts[0].trim();
        String addrPart = parts[1].trim();

        boolean isFpTarget =
                regPart.startsWith("F") || regPart.startsWith("f");
        int regIndex = isFpTarget
                ? parseFpRegister(regPart)
                : parseRegister(regPart);

        // NEW: remember if this memory reg is FP or int
        instr.setMemRegIsFp(isFpTarget);

        // offset(base)
        int lParen = addrPart.indexOf('(');
        int rParen = addrPart.indexOf(')');
        if (lParen == -1 || rParen == -1)
            throw new IllegalArgumentException("Bad address format: " + addrPart);

        long offset = Long.parseLong(addrPart.substring(0, lParen).trim());
        String baseRegStr = addrPart.substring(lParen + 1, rParen).trim();
        int baseReg = parseRegister(baseRegStr);

        instr.setRd(regIndex);      // for load: dest; for store: source
        instr.setRs(baseReg);       // base
        instr.setImmediate(offset); // offset
    }


    // BNE R1, R2, LOOP
    private void parseBranch(String args, Instruction instr) {
        String[] parts = args.split(",");
        if (parts.length != 3)
            throw new IllegalArgumentException("Expected rs, rt, label: " + args);

        int rs = parseRegister(parts[0].trim());
        int rt = parseRegister(parts[1].trim());
        String label = parts[2].trim();

        instr.setRs(rs);
        instr.setRt(rt);
        instr.setBranchLabel(label);
    }

    private int parseRegister(String s) {
        s = s.replaceAll("\\s+", "");
        if (!s.startsWith("R"))
            throw new IllegalArgumentException("Expected integer register, got: " + s);
        return Integer.parseInt(s.substring(1));
    }

    private int parseFpRegister(String s) {
        s = s.replaceAll("\\s+", "");
        if (!s.startsWith("F"))
            throw new IllegalArgumentException("Expected FP register, got: " + s);
        return Integer.parseInt(s.substring(1));
    }
    
    public static void main(String[] args) throws Exception {
        Parser p = new Parser();
        Program prog = p.parse(new File("C:/Users/HP/workspace/simulator/src/test1.txt"));

        for (Instruction instr : prog.getInstructions()) {
            System.out.println(instr.getPcIndex() + ": " + instr.getRawText());
        }

        System.out.println("Labels:");
        prog.getLabelMap().forEach((k,v) ->
            System.out.println(k + " -> " + v));
    }
}
