# PRISMA

A co-op horror extraction roguelite for the browser. Two to four players, one procedurally
generated backrooms floor, and a level that starts in rainbow pastels and sunshine and does
not stay that way.

Open a link, share a six-character room code, descend.

---

## The idea

Every run begins somewhere pleasant. Pastel striped wallpaper, warm sun haze, fluorescent
tubes humming, a music-box melody. It is deliberately *too* friendly.

Then the **descent** begins. One value, `0 → 1`, rises with elapsed time, with the noise you
make, and — most of all — with the fuses you collect. It drives the colour grade, the fog,
the lighting, the entity roster, and the music, all as one continuous interpolation. The
saturation falls, the fog turns from warm white to near-black, the tubes start to stutter
and then die room by room, doorways collapse, and the cheerful opening melody comes back
detuned, slowed, and playing backwards through the reverb.

The game punishes progress. Every fuse you carry to the generator makes the floor more
dangerous. That is the tension the whole design hangs on.

## The run

```
Lobby → Level 0
  → find 3 fuses          the descent rises
  → carry them to the generator
  → the exit powers up    (and the level wakes up)
  → run for it
```

Ten to twenty minutes. Procedurally generated from a seed every time — and if you type a
seed into the lobby, both of you get exactly the same maze.

## What is in it

- **Procedural levels.** BSP partition, braided into loops, with cubicle warrens, corridor
  bundles, pillar halls and atria. Guaranteed connected, guaranteed completable — including
  after every doorway the descent seals behind you.
- **The Blind One.** Hunts by sound alone. Sprinting carries about thirty metres; walking,
  about ten; crouching is inaudible. Stand still and it cannot find you. Every lethal move
  is telegraphed for a second and a half first.
- **Flashlight** with a wide and a focused beam, real shadows through doorways, a volumetric
  cone, and a battery that does not come back.
- **Sanity.** Darkness drains it, light restores it, and standing near your friend steadies
  you both. Low sanity produces hallucinations — sounds with no source, a figure at the end
  of a corridor — generated locally per player, so you each see different ones. "Did you see
  that?" / "See what?"
- **Multiplayer** with an authoritative server, client-side prediction, room codes, and a
  90-second grace period so a dropped connection does not end your run.
- **Full menus**, settings, rebindable keys, and complete German and English translations.
- **Accessibility that actually works:** jump-scare intensity as a single scalar down to
  zero, flashing reduction, screen shake off, hallucinations off, and subtitles for every
  sound — because sound carries gameplay information here, not just atmosphere.

Everything you see and hear is generated at runtime. There are no art assets and no audio
files: wallpaper, carpet and ceiling tiles are painted into canvases at load, and every
sound — footsteps, the drone, the stings, the whispering, the melody — is synthesised with
the Web Audio API.

## Playing

```bash
cd game
pnpm install
pnpm run build
pnpm start          # http://localhost:8787
```

One player clicks **Create room** and reads out the code; the other types it into **Join
room**. The host presses **Descend**.

To play over the internet, see [DEPLOY.md](DEPLOY.md).

### Controls

| Key | |
| --- | --- |
| `W` `A` `S` `D` | Move |
| `Shift` | Sprint (costs stamina) |
| `Ctrl` | Crouch — silent, and slow |
| `E` | Interact: take a fuse, insert it, escape, revive a teammate |
| `F` | Flashlight |
| `R` | Beam mode: wide or focused |
| `F3` | Debug overlay |

All rebindable in Settings → Controls.

## Development

```bash
pnpm run dev         # client on :5173, server on :8787
pnpm run typecheck
pnpm test            # unit and property tests
pnpm run test:e2e    # headless browser tests, including two clients in one maze
pnpm run level <seed>              # print a generated maze as ASCII
pnpm run shots                     # drive the real game headlessly and screenshot it
```

### Layout

```
packages/shared/   the determinism boundary: PRNG, level generation, simulation, protocol
packages/client/   Three.js renderer, Web Audio engine, UI
packages/server/   authoritative simulation, entity AI, rooms
e2e/               Playwright specs
```

`shared` is imported as TypeScript source by both sides. That is the point: the level
generator and the movement function have exactly one implementation, and the client and the
server run the same code. The client regenerates the maze from the seed and asserts its
hash against the server's; on a mismatch it falls back to downloading the tile grid, so a
determinism bug costs four kilobytes instead of putting two players in different buildings.

### Testing

The valuable coverage is in properties, not in screenshots:

- level generation is deterministic across thousands of seeds, and every objective stays
  reachable after each descent event is applied in turn;
- client prediction reproduces the authoritative simulation *exactly* under latency, jitter
  and stalls;
- the Blind One never hunts a player who is crouched and still;
- every translation key and interpolation placeholder exists in both languages.

The browser tests assert liveness — that a frame has a real spread of brightness, that no
WebGL context was lost, that the console is clean — rather than comparing pixels, because
software-rendered output drifts between Chromium versions and a pixel gate would be
permanently flaky.

## Status

Playable end to end: generation, the full run loop, the descent, one entity, the flashlight,
audio, menus, and two-player co-op.

Next, in order: the remaining tools (glowsticks, chalk, camcorder, EMF, almond water,
medkit, radio, decoy, door wedge) with backpack slots; the rest of the entity roster (the
Smiler, the Watcher, the Partygoer, the Doppelgänger); the hub with a shop and unlocks; and
the warehouse and pipe themes.
