@echo off
REM Compile core and Swing GUI and run the Swing UI
set SRC_DIR=src
set OUT_DIR=bin\classes
if not exist %OUT_DIR% mkdir %OUT_DIR%

rem Compile core sources and the Swing GUI only (exclude JavaFX MainApp)
javac -encoding Cp1252 -d %OUT_DIR% -sourcepath %SRC_DIR% %SRC_DIR%\core\*.java %SRC_DIR%\gui\SwingMain.java
if errorlevel 1 (
  echo Compilation failed
  exit /b 1
)

echo Running Swing GUI...
java -cp %OUT_DIR% gui.SwingMain
