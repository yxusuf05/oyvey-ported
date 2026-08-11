#!/usr/bin/env bash
#
# Prepares a local Paper server in server/run/ and installs the built plugin.
# Safe to re-run: existing configs and worlds are never overwritten.
#
#   ./scripts/setup.sh --accept-eula     accept Mojang's EULA non-interactively
#   ./scripts/setup.sh --update          re-download Paper even if present
#   ./scripts/setup.sh --skip-build      don't run Gradle, just wire up the server
#
set -euo pipefail

MC_VERSION="1.21.11"
USER_AGENT="corepvp-setup/1.0 (github.com/yxusuf05/oyvey-ported)"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(dirname "$SCRIPT_DIR")"
RUN_DIR="$SERVER_DIR/run"

ACCEPT_EULA=0
FORCE_UPDATE=0
SKIP_BUILD=0

for arg in "$@"; do
    case "$arg" in
        --accept-eula) ACCEPT_EULA=1 ;;
        --update)      FORCE_UPDATE=1 ;;
        --skip-build)  SKIP_BUILD=1 ;;
        -h|--help)     sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $arg" >&2; exit 2 ;;
    esac
done

say() { printf '\033[36m==>\033[0m %s\n' "$1"; }
die() { printf '\033[31mError:\033[0m %s\n' "$1" >&2; exit 1; }

command -v curl >/dev/null 2>&1 || die "curl is required."

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        echo ""
    fi
}

# --------------------------------------------------------------------------
# 1. Build the plugin
# --------------------------------------------------------------------------
if [ "$SKIP_BUILD" -eq 0 ]; then
    say "Building the plugin"
    (cd "$SERVER_DIR" && ./gradlew shadowJar -q)
fi

PLUGIN_JAR="$(ls -t "$SERVER_DIR"/build/libs/CorePvP-*.jar 2>/dev/null | head -1 || true)"
[ -n "$PLUGIN_JAR" ] || die "No plugin jar found. Run without --skip-build first."

mkdir -p "$RUN_DIR/plugins" "$RUN_DIR/config"

# --------------------------------------------------------------------------
# 2. Download Paper
# --------------------------------------------------------------------------
PAPER_JAR="$RUN_DIR/paper.jar"
if [ ! -f "$PAPER_JAR" ] || [ "$FORCE_UPDATE" -eq 1 ]; then
    say "Resolving the latest stable Paper $MC_VERSION build"
    META="$(curl -sSf -H "User-Agent: $USER_AGENT" \
        "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest")" \
        || die "Could not reach the Paper download API."

    URL="$(printf '%s' "$META" | grep -o '"url":"[^"]*"' | head -1 | cut -d'"' -f4)"
    SHA="$(printf '%s' "$META" | grep -o '"sha256":"[^"]*"' | head -1 | cut -d'"' -f4)"
    BUILD="$(printf '%s' "$META" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)"
    [ -n "$URL" ] || die "Could not parse a download URL out of the API response."

    say "Downloading Paper $MC_VERSION build $BUILD"
    curl -sSf -H "User-Agent: $USER_AGENT" -o "$PAPER_JAR.tmp" "$URL" || die "Download failed."

    if [ -n "$SHA" ]; then
        ACTUAL="$(sha256_of "$PAPER_JAR.tmp")"
        if [ -n "$ACTUAL" ] && [ "$ACTUAL" != "$SHA" ]; then
            rm -f "$PAPER_JAR.tmp"
            die "Checksum mismatch (expected $SHA, got $ACTUAL)."
        fi
        [ -n "$ACTUAL" ] && say "Checksum verified"
    fi
    mv "$PAPER_JAR.tmp" "$PAPER_JAR"
else
    say "Paper already present (use --update to refresh)"
fi

# --------------------------------------------------------------------------
# 3. Server configuration - copied only when missing, never clobbered
# --------------------------------------------------------------------------
copy_if_absent() {
    if [ -f "$2" ]; then
        echo "    kept  $(basename "$2")"
    else
        cp "$1" "$2"
        echo "    added $(basename "$2")"
    fi
}

say "Installing server configuration"
for file in "$SERVER_DIR"/runtime/*.yml "$SERVER_DIR"/runtime/*.properties; do
    [ -e "$file" ] || continue
    copy_if_absent "$file" "$RUN_DIR/$(basename "$file")"
done
for file in "$SERVER_DIR"/runtime/config/*.yml; do
    [ -e "$file" ] || continue
    copy_if_absent "$file" "$RUN_DIR/config/$(basename "$file")"
done

# --------------------------------------------------------------------------
# 4. Plugin
# --------------------------------------------------------------------------
say "Installing $(basename "$PLUGIN_JAR")"
rm -f "$RUN_DIR"/plugins/CorePvP-*.jar
cp "$PLUGIN_JAR" "$RUN_DIR/plugins/"

# --------------------------------------------------------------------------
# 5. EULA - never accepted on the user's behalf without them saying so
# --------------------------------------------------------------------------
if grep -qs '^eula=true' "$RUN_DIR/eula.txt"; then
    say "EULA already accepted"
elif [ "$ACCEPT_EULA" -eq 1 ]; then
    echo "eula=true" > "$RUN_DIR/eula.txt"
    say "EULA accepted via --accept-eula"
else
    echo
    echo "The server will not start until you accept Mojang's EULA:"
    echo "  https://aka.ms/MinecraftEULA"
    printf "Do you accept it? [y/N] "
    read -r answer
    case "$answer" in
        [yY]*) echo "eula=true" > "$RUN_DIR/eula.txt"; say "EULA accepted" ;;
        *) echo "Not accepted. Write 'eula=true' into run/eula.txt when you are ready." ;;
    esac
fi

echo
say "Done. Start the server with:  ./scripts/start.sh"
say "Then connect a Minecraft $MC_VERSION client to  localhost:25565"
