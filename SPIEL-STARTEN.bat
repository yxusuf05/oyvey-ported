@echo off
rem ===========================================================================
rem  PRISMA starten. Einfach doppelklicken.
rem
rem  Diese Datei existiert nur, weil direkt daneben gradlew.bat liegt - das
rem  Build-Skript eines voellig anderen Projekts in diesem Repository. Ein
rem  Doppelklick darauf bricht sofort ab und schliesst das Fenster wieder.
rem  Das hier ist die richtige Datei.
rem
rem  Start PRISMA. Just double-click. The gradlew.bat next to it belongs to an
rem  unrelated project and will only close on you.
rem ===========================================================================

setlocal
cd /d "%~dp0"

if exist "game\start.bat" goto run

echo.
echo   ===============================================
echo   FEHLER: game\start.bat wurde nicht gefunden.
echo   Diese Datei muss im obersten Ordner des Repositories liegen.
echo.
echo   ERROR: game\start.bat is missing. Keep this file in the repository root.
echo   ===============================================
echo.
pause
exit /b 1

:run
call "game\start.bat" %*
exit /b %errorlevel%
