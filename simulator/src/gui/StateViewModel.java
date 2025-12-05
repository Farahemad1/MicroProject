package gui;

import core.*;

import java.io.File;
import java.io.IOException;

/**
 * View model that wraps the Tomasulo engine and provides convenience methods
 * for the JavaFX UI to load a program, configure the engine, and step cycles.
 */
public class StateViewModel {

	private Parser parser = new Parser();
	private Program program;

	private TomasuloEngine engine;

	// Current configuration (defaults)
	public int numFpAddRS = 3;
	public int numFpMulRS = 2;
	public int numIntAluRS = 3;
	public int numLoadBuffers = 3;
	public int numStoreBuffers = 3;

	public int fpAddLatency = 2;
	public int fpMulLatency = 4;
	public int intAluLatency = 1;
	public int loadLatencyBase = 2;
	public int storeLatencyBase = 2;

	// Cache defaults
	public int cacheSize = 1024;
	public int blockSize = 16;
	public int associativity = 2;
	public int cacheHitLatency = 1;
	public int cacheMissPenalty = 10;

	public void loadProgram(File file) throws IOException {
		this.program = parser.parse(file);
	}

	public boolean hasProgram() {
		return program != null;
	}

	public void createEngineWithCurrentConfig() {
		// Create fresh components and engine using the currently-set parameters
		RegisterFile rf = new RegisterFile();
		RegisterStatus rs = new RegisterStatus();
		Memory mem = new Memory();
		Cache cache = new Cache(cacheSize, blockSize, associativity, cacheHitLatency, cacheMissPenalty, mem);

		this.engine = new TomasuloEngine(
				program,
				rf,
				rs,
				mem,
				cache,
				numFpAddRS,
				numFpMulRS,
				numIntAluRS,
				numLoadBuffers,
				numStoreBuffers,
				fpAddLatency,
				fpMulLatency,
				intAluLatency,
				loadLatencyBase,
				storeLatencyBase
		);
	}

	public TomasuloEngine getEngine() {
		return engine;
	}

	public CycleState getCurrentState() {
		if (engine == null) return null;
		return engine.getCurrentState();
	}

	public void nextCycle() {
		if (engine == null) return;
		engine.nextCycle();
	}

	public void previousCycle() {
		if (engine == null) return;
		engine.previousCycle();
	}

	public RegisterStatus getRegisterStatus() {
		if (engine == null) return null;
		return engine.getRegisterStatus();
	}
}
