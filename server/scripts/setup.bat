@echo off
REM Prepares a local Paper server in server\run\ and installs the built plugin.
REM Usage: scripts\setup.bat [--accept-eula] [--update] [--skip-build]
setlocal enabledelayedexpansion

set "MC_VERSION=1.21.11"
set "USER_AGENT=corepvp-setup/1.0 (github.com/yxusuf05/oyvey-ported)"

set "SCRIPT_DIR=%~dp0"
set "SERVER_DIR=%SCRIPT_DIR%.."
set "RUN_DIR=%SERVER_DIR%\run"

set "ACCEPT_EULA=0"
set "FORCE_UPDATE=0"
set "SKIP_BUILD=0"
:parse
if "%~1"=="" goto parsed
if /i "%~1"=="--accept-eula" set "ACCEPT_EULA=1"
if /i "%~1"=="--update" set "FORCE_UPDATE=1"
if /i "%~1"=="--skip-build" set "SKIP_BUILD=1"
shift
goto parse
:parsed

if "%SKIP_BUILD%"=="0" (
    echo ==^> Building the plugin
    pushd "%SERVER_DIR%" || exit /b 1
    call gradlew.bat shadowJar -q || (popd & exit /b 1)
    popd
)

if not exist "%RUN_DIR%\plugins" mkdir "%RUN_DIR%\plugins"
if not exist "%RUN_DIR%\config" mkdir "%RUN_DIR%\config"

if not exist "%RUN_DIR%\paper.jar" set "FORCE_UPDATE=1"
if "%FORCE_UPDATE%"=="1" (
    echo ==^> Downloading Paper %MC_VERSION%
    powershell -NoProfile -Command ^
      "$h=@{'User-Agent'='%USER_AGENT%'};" ^
      "$m=Invoke-RestMethod -Headers $h -Uri 'https://fill.papermc.io/v3/projects/paper/versions/%MC_VERSION%/builds/latest';" ^
      "$d=$m.downloads.'server:default';" ^
      "Invoke-WebRequest -Headers $h -Uri $d.url -OutFile '%RUN_DIR%\paper.jar.tmp';" ^
      "$a=(Get-FileHash '%RUN_DIR%\paper.jar.tmp' -Algorithm SHA256).Hash.ToLower();" ^
      "if ($a -ne $d.checksums.sha256) { Remove-Item '%RUN_DIR%\paper.jar.tmp'; throw 'Checksum mismatch' };" ^
      "Move-Item -Force '%RUN_DIR%\paper.jar.tmp' '%RUN_DIR%\paper.jar'" || exit /b 1
)

echo ==^> Installing server configuration
for %%f in ("%SERVER_DIR%\runtime\*.yml" "%SERVER_DIR%\runtime\*.properties") do (
    if not exist "%RUN_DIR%\%%~nxf" copy /y "%%f" "%RUN_DIR%\" >nul
)
for %%f in ("%SERVER_DIR%\runtime\config\*.yml") do (
    if not exist "%RUN_DIR%\config\%%~nxf" copy /y "%%f" "%RUN_DIR%\config\" >nul
)

echo ==^> Installing the plugin
del /q "%RUN_DIR%\plugins\CorePvP-*.jar" 2>nul
for /f "delims=" %%f in ('dir /b /o-d "%SERVER_DIR%\build\libs\CorePvP-*.jar" 2^>nul') do (
    copy /y "%SERVER_DIR%\build\libs\%%f" "%RUN_DIR%\plugins\" >nul
    goto copied
)
echo No plugin jar found. Run without --skip-build first.
exit /b 1
:copied

findstr /b /c:"eula=true" "%RUN_DIR%\eula.txt" >nul 2>&1
if %errorlevel%==0 (
    echo ==^> EULA already accepted
) else if "%ACCEPT_EULA%"=="1" (
    echo eula=true> "%RUN_DIR%\eula.txt"
    echo ==^> EULA accepted via --accept-eula
) else (
    echo.
    echo The server will not start until you accept Mojang's EULA:
    echo   https://aka.ms/MinecraftEULA
    set /p "answer=Do you accept it? [y/N] "
    if /i "!answer!"=="y" (
        echo eula=true> "%RUN_DIR%\eula.txt"
        echo ==^> EULA accepted
    ) else (
        echo Not accepted. Write "eula=true" into run\eula.txt when you are ready.
    )
)

echo.
echo ==^> Done. Start the server with:  scripts\start.bat
endlocal
