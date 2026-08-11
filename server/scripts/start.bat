@echo off
REM Starts the local Paper server prepared by setup.bat.
REM Heap size:  set MEMORY=6G  before running.
setlocal

if "%MEMORY%"=="" set "MEMORY=4G"

set "RUN_DIR=%~dp0..\run"

if not exist "%RUN_DIR%\paper.jar" (
    echo run\paper.jar is missing - run scripts\setup.bat first.
    exit /b 1
)
findstr /b /c:"eula=true" "%RUN_DIR%\eula.txt" >nul 2>&1
if not %errorlevel%==0 (
    echo Mojang's EULA has not been accepted yet - run scripts\setup.bat.
    exit /b 1
)

cd /d "%RUN_DIR%"

java -Xms%MEMORY% -Xmx%MEMORY% ^
 -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ^
 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ^
 -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M ^
 -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 ^
 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 ^
 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 ^
 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 ^
 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true ^
 -jar paper.jar --nogui %*

endlocal
