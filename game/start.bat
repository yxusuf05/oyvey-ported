@echo off
rem ===========================================================================
rem  PRISMA - Doppelklick-Starter fuer Windows.
rem
rem  Zwilling von start.sh; beide machen dieselben Schritte in derselben
rem  Reihenfolge. Wer hier etwas aendert, aendert es dort meistens auch.
rem
rem  Wichtigste Eigenschaft: das Fenster geht NIE wortlos zu. Jeder Fehler
rem  wird benannt und wartet auf einen Tastendruck.
rem
rem  Optional:  start.bat rebuild   erzwingt neues Installieren und Bauen.
rem ===========================================================================

setlocal

rem Den eigenen Pfad merken, BEVOR irgendwo hin gewechselt wird. %~f0 wird gegen
rem das AKTUELLE Verzeichnis aufgeloest, nicht gegen den Skriptort. Wer diese
rem Datei ueber  call "game\start.bat"  startet, hat in %0 den relativen Pfad
rem stehen - und nach dem cd unten wuerde daraus  ...\game\game\start.bat.
set "SELF=%~f0"

cd /d "%~dp0"

if not defined PORT set "PORT=8787"
set "NODE_MIN=22"
set "STAMPFILE=.build-stamp"
set "COREPACK_ENABLE_DOWNLOAD_PROMPT=0"

rem Der Browser wird von einer zweiten Kopie dieses Skripts geoeffnet, damit
rem der Server nicht warten muss und die Anfuehrungszeichen einfach bleiben.
if /i "%~1"=="--open-browser" goto openbrowser

echo.
echo   PRISMA
echo   ------
echo.

rem --- 1. Node -------------------------------------------------------------

where node >nul 2>nul
if errorlevel 1 goto nonode

for /f "usebackq delims=" %%v in (`node -p "process.versions.node.split('.')[0]" 2^>nul`) do set "NODEMAJOR=%%v"
if not defined NODEMAJOR goto badnode
if %NODEMAJOR% LSS %NODE_MIN% goto oldnode

for /f "usebackq delims=" %%v in (`node --version 2^>nul`) do set "NODEVERSION=%%v"
echo   Node.js %NODEVERSION% gefunden.

rem --- 2. pnpm -------------------------------------------------------------

rem Corepack liegt Node bei. Das ist also keine zusaetzliche Installation,
rem sondern nur ein Schalter.
where pnpm >nul 2>nul
if not errorlevel 1 goto havepnpm

echo   pnpm fehlt, aktiviere Corepack ...
call corepack enable pnpm >nul 2>nul
where pnpm >nul 2>nul
if not errorlevel 1 goto havepnpm

echo   Corepack hat nicht geklappt, installiere pnpm ueber npm ...
call npm install -g pnpm >nul 2>nul
where pnpm >nul 2>nul
if errorlevel 1 goto nopnpm

:havepnpm

rem --- 3. Installieren und bauen, aber nur wenn noetig ----------------------

rem Der Stempel ist der Commit, aus dem der vorhandene Build stammt. Nach
rem einem git pull passt er nicht mehr und es wird neu gebaut; auf einem
rem unveraenderten Stand startet das Spiel sofort.
set "HEADSTAMP=nogit"
where git >nul 2>nul
if not errorlevel 1 for /f "usebackq delims=" %%h in (`git rev-parse HEAD 2^>nul`) do set "HEADSTAMP=%%h"

set "NEEDBUILD="
if not exist "node_modules\." set "NEEDBUILD=1"
if not exist "packages\server\dist\index.js" set "NEEDBUILD=1"
if not exist "packages\client\dist\index.html" set "NEEDBUILD=1"

rem Das Einlesen steht bewusst auf einer eigenen Zeile. cmd wertet die
rem Umleitung schon beim Zerlegen der Zeile aus, also wuerde ein
rem  if exist datei set /p X=^<datei  beim ersten Start eine Fehlermeldung
rem ueber die noch fehlende Datei ausspucken, obwohl das if gar nicht greift.
set "OLDSTAMP="
if not exist "%STAMPFILE%" goto nostamp
set /p OLDSTAMP=<"%STAMPFILE%"
:nostamp
if not "%OLDSTAMP%"=="%HEADSTAMP%" set "NEEDBUILD=1"
if /i "%~1"=="rebuild" set "NEEDBUILD=1"

if not defined NEEDBUILD goto skipbuild

echo.
echo   Erster Start oder neue Version: installieren und bauen.
echo   Das dauert ein paar Minuten und passiert nur dieses eine Mal.
echo.

call pnpm install
if errorlevel 1 goto installfailed

call pnpm run build
if errorlevel 1 goto buildfailed

>"%STAMPFILE%" echo %HEADSTAMP%
echo.
echo   Fertig gebaut.

:skipbuild

rem --- 4. Browser ----------------------------------------------------------

start "" /min "%SELF%" --open-browser

rem --- 5. Starten ----------------------------------------------------------

echo.
node packages\server\dist\index.js
set "STATUS=%errorlevel%"
if not "%STATUS%"=="0" goto serverdied
goto halt

rem =========================================================================
rem  Meldungen
rem =========================================================================

:nonode
echo.
echo   ===============================================
echo   FEHLER: Node.js ist nicht installiert.
echo.
echo   1. Oeffne https://nodejs.org und lade die LTS-Version, %NODE_MIN% oder neuer.
echo   2. Installiere sie mit allen Vorgaben.
echo   3. WICHTIG: schliesse dieses Fenster und starte start.bat neu.
echo      Windows kennt neue Programme nur in NEU geoeffneten Fenstern.
echo.
echo   ERROR: Node.js is not installed. Install the LTS build from
echo   https://nodejs.org, then close this window and run start.bat again.
echo   ===============================================
goto halt

:badnode
echo.
echo   ===============================================
echo   FEHLER: Node.js laesst sich nicht abfragen.
echo   Installiere die LTS-Version von https://nodejs.org neu.
echo.
echo   ERROR: Could not read the Node.js version.
echo   Reinstall the LTS build from https://nodejs.org.
echo   ===============================================
goto halt

:oldnode
echo.
echo   ===============================================
echo   FEHLER: Node.js %NODEMAJOR% ist zu alt.
echo   Gebraucht wird mindestens Version %NODE_MIN%.
echo   Hol dir die LTS-Version von https://nodejs.org.
echo.
echo   ERROR: Node.js %NODEMAJOR% is too old, %NODE_MIN% or newer is required.
echo   ===============================================
goto halt

:nopnpm
echo.
echo   ===============================================
echo   FEHLER: pnpm konnte nicht eingerichtet werden.
echo   Fuehre in diesem Fenster einmal von Hand aus:
echo.
echo       corepack enable pnpm
echo.
echo   ERROR: pnpm could not be set up. Run  corepack enable pnpm  by hand.
echo   ===============================================
goto halt

:installfailed
echo.
echo   ===============================================
echo   FEHLER: pnpm install ist fehlgeschlagen.
echo   Meistens fehlt die Internetverbindung oder ein Proxy blockiert.
echo   Die Meldungen darueber sagen genauer, woran es lag.
echo.
echo   ERROR: pnpm install failed - usually no internet or a proxy in the way.
echo   ===============================================
goto halt

:buildfailed
echo.
echo   ===============================================
echo   FEHLER: pnpm run build ist fehlgeschlagen.
echo   Die Meldung darueber nennt die Datei, die schuld ist.
echo.
echo   ERROR: pnpm run build failed - the message above names the file.
echo   ===============================================
goto halt

:serverdied
echo.
echo   ===============================================
echo   Der Server hat sich mit Code %STATUS% beendet.
echo   Haeufigste Ursache: Port %PORT% ist belegt, weil PRISMA schon laeuft.
echo   Schliesse das andere PRISMA-Fenster und starte erneut.
echo.
echo   The server exited with code %STATUS%.
echo   Usually port %PORT% is already in use by another PRISMA window.
echo   ===============================================
goto halt

:openbrowser
rem Kurz warten, damit der Server den Port schon hat, wenn der Browser anklopft.
timeout /t 3 /nobreak >nul
start "" "http://localhost:%PORT%"
exit /b 0

:halt
echo.
pause
exit /b
