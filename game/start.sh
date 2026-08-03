#!/usr/bin/env bash
#
# PRISMA — one-click launcher for macOS and Linux.
#
# Its Windows twin is start.bat; the two run the same steps in the same order, so a change
# here almost always needs a change there.
#
# The contract: this script never fails silently. Every step that can go wrong prints what
# went wrong and what to do about it, in German and English, and stops there.
#
#   PRISMA_NO_BROWSER=1   don't open a browser (used by the smoke test)
#   PRISMA_REBUILD=1      force a reinstall and rebuild

cd "$(dirname "$0")" || exit 1

NODE_MIN=22
STAMP=".build-stamp"
PORT="${PORT:-8787}"
export COREPACK_ENABLE_DOWNLOAD_PROMPT=0

say() { printf '%s\n' "$*"; }

# Two lines, two languages. The English one exists because a launcher that only speaks one
# language is a launcher half the people who hit an error cannot read.
fail() {
  say ""
  say "  ==============================================="
  say "  FEHLER: $1"
  say "  ERROR:  $2"
  say "  ==============================================="
  say ""
  exit 1
}

say ""
say "  PRISMA"
say "  ------"
say ""

# --- 1. Node -----------------------------------------------------------------------------

if ! command -v node >/dev/null 2>&1; then
  fail "Node.js ist nicht installiert. Lade es von https://nodejs.org (Version ${NODE_MIN} LTS), schliesse danach dieses Fenster und oeffne ein NEUES." \
       "Node.js is not installed. Get the ${NODE_MIN} LTS build from https://nodejs.org, then close this window and open a NEW one."
fi

NODE_MAJOR="$(node -p 'process.versions.node.split(".")[0]' 2>/dev/null)"
case "$NODE_MAJOR" in
  '' | *[!0-9]*)
    fail "Node.js laesst sich nicht abfragen. Installiere Version ${NODE_MIN} LTS neu von https://nodejs.org." \
         "Could not read the Node.js version. Reinstall the ${NODE_MIN} LTS build from https://nodejs.org."
    ;;
esac

if [ "$NODE_MAJOR" -lt "$NODE_MIN" ]; then
  fail "Node.js $NODE_MAJOR ist zu alt, gebraucht wird mindestens $NODE_MIN. Hol dir die LTS-Version von https://nodejs.org." \
       "Node.js $NODE_MAJOR is too old; $NODE_MIN or newer is required. Get the LTS build from https://nodejs.org."
fi

say "  Node.js $(node --version) gefunden."

# --- 2. pnpm -----------------------------------------------------------------------------

# Corepack ships with Node, so this is not an extra install — it just switches pnpm on.
if ! command -v pnpm >/dev/null 2>&1; then
  say "  pnpm fehlt, aktiviere Corepack ..."
  corepack enable pnpm >/dev/null 2>&1
fi
if ! command -v pnpm >/dev/null 2>&1; then
  say "  Corepack hat nicht geklappt, installiere pnpm ueber npm ..."
  npm install -g pnpm >/dev/null 2>&1
fi
if ! command -v pnpm >/dev/null 2>&1; then
  fail "pnpm konnte nicht eingerichtet werden. Fuehre einmal von Hand aus:  corepack enable pnpm" \
       "pnpm could not be set up. Run this once by hand:  corepack enable pnpm"
fi

# --- 3. Install and build, but only when something actually changed -----------------------

# The stamp is the commit the current build came from. After a `git pull` it no longer
# matches and the build repeats itself; on an unchanged checkout every start is instant.
current_stamp() {
  local head=''
  if command -v git >/dev/null 2>&1; then
    head="$(git rev-parse HEAD 2>/dev/null)"
  fi
  printf '%s' "${head:-nogit}"
}

need_build=0
[ -d node_modules ] || need_build=1
[ -f packages/server/dist/index.js ] || need_build=1
[ -f packages/client/dist/index.html ] || need_build=1
[ "$(cat "$STAMP" 2>/dev/null)" = "$(current_stamp)" ] || need_build=1
[ -z "${PRISMA_REBUILD:-}" ] || need_build=1

if [ "$need_build" = "1" ]; then
  say ""
  say "  Erster Start (oder neue Version): installieren und bauen."
  say "  Das dauert ein paar Minuten und passiert nur dieses eine Mal."
  say ""

  if ! pnpm install; then
    fail "'pnpm install' ist fehlgeschlagen. Meistens fehlt Internet oder es haengt ein Firmen-Proxy dazwischen." \
         "'pnpm install' failed. Usually that means no internet connection or a corporate proxy in the way."
  fi

  if ! pnpm run build; then
    fail "'pnpm run build' ist fehlgeschlagen. Die Meldung darueber sagt, welche Datei schuld ist." \
         "'pnpm run build' failed. The message above names the file at fault."
  fi

  current_stamp >"$STAMP"
  say ""
  say "  Fertig gebaut."
fi

# --- 4. Browser --------------------------------------------------------------------------

# Backgrounded with a short delay: the server needs a moment to bind the port, and a browser
# that arrives first shows a connection error the user then has to reload away.
if [ -z "${PRISMA_NO_BROWSER:-}" ]; then
  url="http://localhost:${PORT}"
  if command -v open >/dev/null 2>&1; then
    (sleep 2 && open "$url") >/dev/null 2>&1 &
  elif command -v xdg-open >/dev/null 2>&1; then
    (sleep 2 && xdg-open "$url") >/dev/null 2>&1 &
  fi
fi

# --- 5. Run ------------------------------------------------------------------------------

say ""
node packages/server/dist/index.js
status=$?

if [ "$status" -ne 0 ] && [ "$status" -ne 130 ]; then
  say ""
  say "  Der Server hat sich mit Code $status beendet."
  say "  Haeufigste Ursache: Port ${PORT} ist belegt, weil PRISMA schon laeuft."
  say "  The server exited with code $status — usually port ${PORT} is already in use."
  say ""
fi

exit "$status"
