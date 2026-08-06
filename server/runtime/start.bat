@echo off
REM Starts the local test server on Windows. Downloads the Paper jar on first run.
setlocal enabledelayedexpansion

cd /d "%~dp0"

set MC_VERSION=1.21.11
set PAPER_JAR=paper-%MC_VERSION%.jar
if "%MIN_RAM%"=="" set MIN_RAM=2G
if "%MAX_RAM%"=="" set MAX_RAM=4G

if not exist "%PAPER_JAR%" (
  echo Paper %MC_VERSION% is missing, downloading the latest build...
  powershell -NoProfile -Command ^
    "$meta = Invoke-RestMethod 'https://fill.papermc.io/v3/projects/paper/versions/%MC_VERSION%/builds/latest';" ^
    "$download = $meta.downloads.'server:default';" ^
    "Invoke-WebRequest $download.url -OutFile '%PAPER_JAR%';" ^
    "$hash = (Get-FileHash '%PAPER_JAR%' -Algorithm SHA256).Hash.ToLower();" ^
    "if ($hash -ne $download.checksums.sha256) { Remove-Item '%PAPER_JAR%'; throw 'checksum mismatch' }"
  if errorlevel 1 (
    echo Download failed.
    exit /b 1
  )
)

if not exist eula.txt goto :noeula
findstr /b /c:"eula=true" eula.txt >nul || goto :noeula

if not exist plugins mkdir plugins

java -Xms%MIN_RAM% -Xmx%MAX_RAM% ^
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC ^
  -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 ^
  -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 ^
  -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 ^
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 ^
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 ^
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true ^
  -jar "%PAPER_JAR%" --nogui %*
goto :eof

:noeula
echo.
echo   The Minecraft EULA has not been accepted yet.
echo   Read https://aka.ms/MinecraftEULA and, if you agree, run:
echo.
echo       echo eula=true^> eula.txt
echo.
exit /b 1
