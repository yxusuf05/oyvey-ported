#!/usr/bin/env bash
# Starts PRISMA from the repository root. The real launcher is game/start.sh; this is here
# so the game is findable without knowing which of the two projects in this repository it
# lives in.
cd "$(dirname "$0")" || exit 1
exec ./game/start.sh "$@"
