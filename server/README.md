# CorePvP

A complete PvP server in the shape of a practice network: lobby, kits, arenas,
queues, ranked ELO, duels, parties, FFA, a crystal-PvP focus and a survival
world — all in one Paper plugin you run locally, with no hosting involved.

> This is a **separate project** from the Fabric client mod in the repository
> root. It has its own Gradle build and shares nothing with it. The root build
> is untouched by anything in this folder.

## Requirements

- **Java 21**
- A Minecraft **1.21.11** client
- `curl` (Linux/macOS) or PowerShell (Windows) for the setup script

## Quick start

```bash
cd server
./scripts/setup.sh      # builds the plugin, downloads Paper, asks about the EULA
./scripts/start.sh      # starts the server on localhost:25565
```

On Windows use `scripts\setup.bat` and `scripts\start.bat`.

`setup.sh` is safe to re-run — it never overwrites configs or worlds you have
changed. Useful flags:

| Flag | Effect |
|---|---|
| `--accept-eula` | Accept Mojang's EULA without the prompt |
| `--update` | Re-download Paper even if it is already there |
| `--skip-build` | Skip Gradle and only re-install the existing jar |

Heap size is configurable: `MEMORY=6G ./scripts/start.sh`.

## Layout

```
server/
├── build.gradle          standalone build (Paper 1.21.11, Java 21, shadow)
├── scripts/              setup + start, for Linux/macOS and Windows
├── runtime/              config templates, copied into run/ on first setup
├── run/                  the actual server (git-ignored, safe to delete)
└── src/main/java/me/alpha432/corepvp/
```

Delete `run/` any time to start from a clean server; nothing in it is source of
truth except the worlds and `plugins/CorePvP/`.

## Building

```bash
./gradlew build      # compiles, runs tests, produces build/libs/CorePvP-<version>.jar
./gradlew test       # unit tests only (no server required)
```

The tests are deliberately free of Bukkit types, so they run in seconds without
a server.

## Configuration

Files live in `run/plugins/CorePvP/` after the first start:

| File | Contents |
|---|---|
| `config.yml` | Storage, profiles, and per-subsystem settings |
| `messages.yml` | Every piece of player-facing text (MiniMessage) |

`/corepvp reload` re-reads them at runtime.

**Storage** defaults to SQLite (`data.db` in the plugin folder), which needs no
setup at all. Switch `storage.type` to `MYSQL` and fill in the credentials when
you want several servers to share one database.

## Client versions

The server targets 1.21.11. To let older clients (1.8 and up) connect, drop
[ViaVersion](https://hangar.papermc.io/ViaVersion/ViaVersion) and ViaBackwards
into `run/plugins/`. They are not bundled here.

## Notes on running this publicly

`server.properties` ships with `online-mode=true`. If you turn it off for
offline testing, do not expose the port to the internet — anyone could join
under any name.
