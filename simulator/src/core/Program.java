package core;

import java.util.List;
import java.util.Map;

public class Program {
    private final List<Instruction> instructions;
    private final Map<String, Integer> labelToPcIndex;

    public Program(List<Instruction> instructions,
                   Map<String, Integer> labelToPcIndex) {
        this.instructions = instructions;
        this.labelToPcIndex = labelToPcIndex;
    }

    public Instruction getInstruction(int pcIndex) {
        if (pcIndex < 0 || pcIndex >= instructions.size()) return null;
        return instructions.get(pcIndex);
    }

    public int size() {
        return instructions.size();
    }

    public Integer getLabelTarget(String label) {
        return labelToPcIndex.get(label);
    }

    public List<Instruction> getInstructions() {
        return instructions;
    }

    public Map<String, Integer> getLabelMap() {
        return labelToPcIndex;
    }
}