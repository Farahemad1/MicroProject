Tomasulo Simulator (GUI + Core)

Overview
- This repository contains a Tomasulo algorithm simulator core (`src/core`) and a simple JavaFX GUI (`src/gui`).
- The GUI displays register file, reservation stations, load/store buffers and instruction timing, and lets you step cycle-by-cycle.

Requirements
- JDK 11+ installed and on PATH.
- For GUI: JavaFX SDK matching your JDK. Download from https://openjfx.io and note the `lib` folder path (e.g. `C:\javafx-sdk-20\lib`).

Quick run (core engine only)
- Compile and run the command-line test engine with the included helper:

```cmd
cd /d C:\Users\farah\Desktop\microproj\simulator
run_core.bat
```
- Or compile manually:

```cmd
javac -encoding Cp1252 -d bin\classes -sourcepath src src\core\*.java
java -cp bin\classes core.TestEngine
```

Run the JavaFX GUI
- Use the provided `run_gui.bat` and give the path to your JavaFX `lib` directory:

```cmd
cd /d C:\Users\farah\Desktop\microproj\simulator
run_gui.bat "C:\javafx-sdk-20\lib"
```

What I implemented
- `StateViewModel` (in `src/gui`) - wraps `Parser` and `TomasuloEngine` and creates the engine with configurable parameters.
- `MainApp` (in `src/gui`) - JavaFX-based UI: load program file, Step / Prev / Reset buttons, tables for registers, reservation stations, load/store buffers, and instruction timing.
- Helper batch scripts `run_core.bat` and `run_gui.bat`.

Notes & next steps
- The GUI requires JavaFX at compile & runtime; provide the SDK lib path when running.
- The cache implementation is basic (bypasses full set-assoc LRU) but cache parameters are configurable via code in `StateViewModel`.
- If you want, I can:
  - Add input controls for all configuration options in the GUI (latencies, sizes, cache params).
  - Implement an automatic Run (play) button with adjustable cycle speed.
  - Improve cache (set-associative with LRU) and show cache hit/miss per access.
  - Export a zipped submission folder containing code and a short report.

If you want me to continue, tell me which feature to prioritize next (e.g., add parameter controls into the GUI, implement full cache, add Play/Auto-run, or produce the final report and zip).