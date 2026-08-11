#!/usr/bin/env bash
#
# Starts the local Paper server prepared by setup.sh.
# Heap size can be overridden:  MEMORY=6G ./scripts/start.sh
#
set -euo pipefail

MEMORY="${MEMORY:-4G}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$(dirname "$SCRIPT_DIR")/run"

[ -f "$RUN_DIR/paper.jar" ] || { echo "run/paper.jar is missing - run ./scripts/setup.sh first." >&2; exit 1; }
if ! grep -qs '^eula=true' "$RUN_DIR/eula.txt"; then
    echo "Mojang's EULA has not been accepted yet - run ./scripts/setup.sh." >&2
    exit 1
fi

cd "$RUN_DIR"

# Aikar's flags: G1 tuned for a low, predictable pause time, which matters far
# more for PvP than raw throughput.
exec java \
    -Xms"$MEMORY" -Xmx"$MEMORY" \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:+AlwaysPreTouch \
    -XX:G1NewSizePercent=30 \
    -XX:G1MaxNewSizePercent=40 \
    -XX:G1HeapRegionSize=8M \
    -XX:G1ReservePercent=20 \
    -XX:G1HeapWastePercent=5 \
    -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=15 \
    -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem \
    -XX:MaxTenuringThreshold=1 \
    -Dusing.aikars.flags=https://mcflags.emc.gs \
    -Daikars.new.flags=true \
    -jar paper.jar --nogui "$@"
