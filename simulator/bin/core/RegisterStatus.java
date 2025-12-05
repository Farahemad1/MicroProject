//package core;
//
//public class RegisterStatus {
//
//    // owner is RS name like "A0", "M1", or null if no pending writer
//    private final String[] intRegOwner = new String[32];
//    private final String[] fpRegOwner  = new String[32];
//
//    public RegisterStatus() {
//        reset();
//    }
//
//    public void reset() {
//        for (int i = 0; i < 32; i++) {
//            intRegOwner[i] = null;
//            fpRegOwner[i] = null;
//        }
//    }
//
//    // ---------- Integer registers ----------
//
//    public String getIntOwner(int index) {
//        if (index < 0 || index >= 32) {
//            throw new IllegalArgumentException("Invalid int register index: R" + index);
//        }
//        // R0 is always 0, never has an owner
//        if (index == 0) return null;
//        return intRegOwner[index];
//    }
//
//    public void setIntOwner(int index, String rsName) {
//        if (index < 0 || index >= 32) {
//            throw new IllegalArgumentException("Invalid int register index: R" + index);
//        }
//        if (index == 0) return; // R0 never owned
//        intRegOwner[index] = rsName;
//    }
//
//    // ---------- FP registers ----------
//
//    public String getFpOwner(int index) {
//        if (index < 0 || index >= 32) {
//            throw new IllegalArgumentException("Invalid FP register index: F" + index);
//        }
//        return fpRegOwner[index];
//    }
//
//    public void setFpOwner(int index, String rsName) {
//        if (index < 0 || index >= 32) {
//            throw new IllegalArgumentException("Invalid FP register index: F" + index);
//        }
//        fpRegOwner[index] = rsName;
//    }
//}
package core;

import java.util.Arrays;

public class RegisterStatus {

    // For integer registers R0..R31: which RS / LB is producing it ("I0", "L1", etc.)
    private final String[] intOwners;

    // For FP registers F0..F31: which RS / LB is producing it
    private final String[] fpOwners;

    public RegisterStatus() {
        intOwners = new String[32];
        fpOwners = new String[32];
        reset();
    }

    public void reset() {
        Arrays.fill(intOwners, null);
        Arrays.fill(fpOwners, null);
    }

    // ---------- INT REGISTERS ----------

    public String getIntOwner(int regIndex) {
        if (regIndex < 0 || regIndex >= intOwners.length) {
            throw new IllegalArgumentException("Bad int reg index: " + regIndex);
        }
        return intOwners[regIndex];
    }

    public void setIntOwner(int regIndex, String owner) {
        if (regIndex < 0 || regIndex >= intOwners.length) {
            throw new IllegalArgumentException("Bad int reg index: " + regIndex);
        }
        intOwners[regIndex] = owner;
    }

    // ---------- FP REGISTERS ----------

    public String getFpOwner(int regIndex) {
        if (regIndex < 0 || regIndex >= fpOwners.length) {
            throw new IllegalArgumentException("Bad FP reg index: " + regIndex);
        }
        return fpOwners[regIndex];
    }

    public void setFpOwner(int regIndex, String owner) {
        if (regIndex < 0 || regIndex >= fpOwners.length) {
            throw new IllegalArgumentException("Bad FP reg index: " + regIndex);
        }
        fpOwners[regIndex] = owner;
    }
}
