# Configuration

Files live in `run/plugins/CorePvP/` after the first start. `/corepvp reload`
re-reads all of them; arenas are deliberately not hot-reloaded, because a
running match holds a live arena object and swapping it out would strand its
rollback journal.

| File | Contents |
|---|---|
| `config.yml` | Everything below |
| `messages.yml` | All player-facing text, as MiniMessage |
| `ranks.yml` | Ranks, prefixes and the permission that grants each |
| `kits.yml` | Kits — written on first start, yours to edit afterwards |
| `arenas.yml` | Arenas — written by the generator |
| `ffa.yml` | Free-for-all arenas |

## Storage

```yaml
storage:
  type: SQLITE     # or MYSQL
```

SQLite needs no setup and keeps everything in `data.db`. It runs in WAL mode
with a single connection, because SQLite serialises writes anyway and a larger
pool only produces `SQLITE_BUSY`. Switch to MySQL when several servers should
share one database; both drivers ship inside the plugin jar.

## Combat

Combat feel is a property of the **kit**, not the server, so 1.8-style and
crystal kits coexist. In `kits.yml`:

```yaml
combat-mode: LEGACY_1_8   # or MODERN, CRYSTAL
```

- `LEGACY_1_8` — no attack cooldown, no sweep attacks
- `MODERN` — untouched vanilla 1.21 combat
- `CRYSTAL` — modern combat plus explosion attribution and totem tracking

## Kit flags

| Flag | Effect |
|---|---|
| `build` | Blocks may be placed and broken; the arena is rolled back afterwards |
| `hunger` | The food bar drains |
| `natural-regen` | Health regenerates on its own |
| `sumo` | No damage; you lose by leaving the platform |
| `boxing` | No death; first to `match.boxing-hits` wins |
| `ranked-enabled` | The kit can be queued ranked |
| `editable` | Players may save hotbar layouts |
| `infinite-items` | Consumables are topped up while fighting |

## Matchmaking

```yaml
queue:
  elo-range-base: 50
  elo-range-expansion-per-second: 25
  elo-range-max: 5000
  k-factor: 32
  ranked-min-games: 0
```

The acceptable rating gap starts at `elo-range-base` and widens while a player
waits. A pair is allowed when *either* side's widened range covers the gap —
that asymmetry is what keeps a quiet server from deadlocking with two people in
the queue.

`k-factor` doubles for a player's first ten matches on a kit and halves above
2000 rating.

## Arenas

```yaml
arenas:
  blocks-per-tick: 4000            # while generating
  rollback-blocks-per-tick: 2000   # after a match
```

Rollback is deliberately slower than generation: it runs while other matches
are being played.

## Crystal PvP

```yaml
crystal:
  attribution-seconds: 30
  anchor-attribution-seconds: 3
  refill:
    END_CRYSTAL: 64
    OBSIDIAN: 64
```

Explosions carry no attacker, so a crystal is credited to whoever placed it and
an anchor to the nearest recent detonation. Refills only top up materials the
kit already contains, so they can never hand out something new.

## Everything else

`match` (countdown, end delay, time limit, combat tag, boxing hits, snapshot
cache), `ffa` (safe radius, combat tag, killstreak milestones, refill on kill),
`party` (max size), `survival` (spawn, protection radius, home limit, teleport
cooldown), `lobby`, `chat`, `scoreboard` and `leaderboard` are all commented
inline in `config.yml`.
