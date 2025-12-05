@echo off
REM Compile core sources and run TestEngine
set SRC_DIR=src
set OUT_DIR=bin\classes
if not exist %OUT_DIR% mkdir %OUT_DIR%

javac -encoding Cp1252 -d %OUT_DIR% -sourcepath %SRC_DIR% %SRC_DIR%\core\*.java
if errorlevel 1 (
  echo Compilation failed
  exit /b 1
)

echo Running TestEngine (uses src\test1.txt by default)...
java -cp %OUT_DIR% core.TestEngine %*
