#!/usr/bin/env bash
# Starts the local test server. Downloads the Paper jar on first run.
set -euo pipefail

cd "$(dirname "$0")"

MC_VERSION="1.21.11"
PAPER_JAR="paper-${MC_VERSION}.jar"
MIN_RAM="${MIN_RAM:-2G}"
MAX_RAM="${MAX_RAM:-4G}"

if [ ! -f "$PAPER_JAR" ]; then
  echo "Paper ${MC_VERSION} is missing, downloading the latest build…"
  META=$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds/latest")
  URL=$(printf '%s' "$META" | grep -o '"url":"[^"]*"' | head -1 | cut -d'"' -f4)
  SHA=$(printf '%s' "$META" | grep -o '"sha256":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ -z "$URL" ]; then
    echo "Could not read the download URL from the PaperMC API." >&2
    exit 1
  fi
  curl -fL --progress-bar -o "$PAPER_JAR" "$URL"
  if command -v sha256sum >/dev/null 2>&1 && [ -n "$SHA" ]; then
    echo "${SHA}  ${PAPER_JAR}" | sha256sum -c - || { rm -f "$PAPER_JAR"; exit 1; }
  fi
fi

if [ ! -f eula.txt ] || ! grep -q '^eula=true' eula.txt; then
  cat <<'MSG'

  The Minecraft EULA has not been accepted yet.
  Read https://aka.ms/MinecraftEULA and, if you agree, run:

      echo "eula=true" > eula.txt

MSG
  exit 1
fi

mkdir -p plugins

# Aikar's flags: good defaults for a small survival/minigame server.
exec java -Xms"${MIN_RAM}" -Xmx"${MAX_RAM}" \
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true \
  -jar "$PAPER_JAR" --nogui "$@"
