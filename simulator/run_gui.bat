@echo off
REM Usage: run_gui.bat <path-to-javafx-sdk-lib>
REM Example: run_gui.bat "C:\javafx-sdk-20\lib"

set JAVAFX_LIB=%1
if "%JAVAFX_LIB%"=="" (
  echo Please provide the path to JavaFX SDK "lib" folder as the first argument.
  echo Example: run_gui.bat "C:\javafx-sdk-20\lib"
  exit /b 1
)

if not exist bin\classes mkdir bin\classes

echo Compiling core and gui sources...
javac -encoding Cp1252 -d bin\classes -sourcepath src --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.fxml src\core\*.java src\gui\*.java
if errorlevel 1 (
  echo Compilation failed.
  exit /b 1
)

echo Running GUI...
java --module-path "%JAVAFX_LIB%" --add-modules javafx.controls,javafx.fxml -cp bin\classes gui.MainApp
