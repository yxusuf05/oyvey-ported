<div align="center">

# 👻 Ghost Client

A modern **Fabric** performance & utility client for Minecraft, in the spirit of Feather / Lunar:
FPS boost, a clean animated HUD and handy quality-of-life modules.

<img src="images/ui.png" width="90%" />

</div>

## Features

### Performance (FPS boost)
- **FpsBoost** — one toggle that lowers the heaviest vanilla render settings and restores them
  when disabled. Configurable: minimal particles, no entity shadows, no clouds, fast graphics
  preset, no view bobbing, smooth-lighting off, biome-blend radius, entity distance scaling and
  an optional render-distance cap.

### HUD (modern, animated)
- **Keystrokes** — Lunar-style WASD + mouse + spacebar overlay. Each key smoothly fades toward the
  client accent color when held, using a frame-rate-independent animator, and can show live CPS on
  the mouse keys.
- **Ghost** — animated branding watermark with a gradient panel and a pulsing accent underline.
- **FPS**, **CPS** (left / right), **Ping**, **ArmorHud** (with durability) and **PotionHud**
  (with remaining time) — all draggable in the HUD editor.

### Combat (crystal PvP)
- **AutoCrystal** — the crystal bot. Picks the closest valid target, searches the surrounding
  obsidian/bedrock for the placement with the highest predicted damage, places a crystal there and
  detonates it. Refuses any action that would cost more of its own health than `MaxSelfDamage`
  allows, relaxes the damage threshold into a face-place once the target is nearly dead, and aims
  via rotation packets so your camera never gets yanked around. Configurable place/break toggles
  and delays, place/break/wall ranges, damage thresholds, auto-swap and a render of the chosen spot.
- **AutoAnchor** — respawn-anchor combat for overworld/end: finds or places an anchor next to the
  target, charges it with glowstone and detonates, using the same damage prediction and self-damage
  guards as AutoCrystal. **SafeAnchor** mode stops at one charge so a utility anchor never blows up
  in your face.
- **Surround** — walls your feet in with obsidian so nobody can crystal you point-blank; refills
  automatically the moment a block is blown out.
- **KillAura** — melee aura with packet rotations, reach/target filters and an optional wait for the
  vanilla attack cooldown so every hit is fully charged.
- **TriggerBot** — auto-attacks the entity under your crosshair with a configurable delay; filters
  for players / mobs / crystals and skips friends. Never fires while a screen is open.
- **AutoTotem** — keeps a Totem of Undying in your off-hand. **Packet** mode swaps instantly;
  **Legit** mode opens your inventory and glides the real cursor onto the totem before clicking, and
  **KeepInHotbar** parks a spare totem in a configurable hotbar slot.
- **Criticals** — packet criticals on your hits.
- **Reach** — extends the client-side entity/block interaction range (mixin on `raycastHitResult`).
  Keep it within the server's tolerance or hits get rejected.
- **Hitboxes** — inflates `Entity#getPickRadius` (ray-trace only, not collision) so targets are
  easier to click.
- **SilentAim** — rotates toward the nearest target for the outgoing move packet only and restores
  your view, so hits land without the camera moving. Pairs with manual clicking / TriggerBot.
- **AimAssist** — softly pulls your real camera toward a target already inside a configurable FOV
  cone (a "legit"-style assist, not a snap).
- **WTap** — sprint-reset on hit for extra knockback.
- **AutoShieldBreaker** — swaps to an axe to break a blocking enemy's shield, then swaps back.
- **KillAura** also gained an **AutoBlock** option (raises an off-hand shield between hits).

Damage decisions come from a client-side reimplementation of the vanilla explosion pipeline
(`DamageUtil`): the raw blast formula, difficulty scaling, armour absorption, resistance and
enchantment protection, in vanilla's order — so "is this worth placing" matches what the server
will actually do.

### Render / ESP
- **ESP** — boxes around players and mobs through walls, with a separate friend colour, adjustable
  fill/outline and range. Positions are interpolated per frame so boxes stay smooth at any FPS.
- **BlockOutline** — fully colour-customizable outline (+ optional fill) of the block you aim at,
  with an **IgnoreCrystals** option so it stops cluttering the obsidian under end crystals, a
  chroma mode and a toggle to replace the vanilla outline.
- **BlockGlint** — highlights crystal-PvP blocks around you (obsidian, crying obsidian, respawn
  anchors, ender chests) with a filled box + outline. Scanning is throttled so it stays cheap.
- **HoleESP** — colours the safe holes around you (full bedrock vs. one-obsidian) for crystal PvP.
- **StorageESP** — boxes around chests, shulkers, barrels, ender chests and furnaces through walls.
- **Fullbright** — see in the dark (restores your gamma on disable).
- **Zoom** — smooth toggle zoom.

### Player / Movement
- **AutoRespawn** — respawns you instantly on death.
- **ToggleSprint** — auto-sprint while moving.
- **Step / ReverseStep / NoFall / Velocity / FastPlace** — movement & interaction helpers.

### ClickGui (modern design)
The ClickGui uses rounded, glass-style panels with an accent header, an animated hover/enable
highlight on every module row and a configurable **corner rounding** and accent colour (with an
optional rainbow mode). Open it with `Right Shift`.

All modules are toggleable and configurable from the ClickGui (`Right Shift` by default) or via
chat commands (prefix `.`). Drag HUD elements around with the **HudEditor** module.

## Building

Requires JDK 21.

```bash
./gradlew build
```

The mod jar is produced in `build/libs/`.

## Minecraft versions

This project targets **Fabric** and is built against **1.21.11** (see `gradle.properties`).
Because Minecraft's internal APIs and mappings change between minor releases, a single jar can't
cover the whole 1.21–1.21.11 range at once — the way Feather/Lunar do it is a separate build per
version. To target another version, adjust `minecraft_version`, `yarn_mappings`, `loader_version`
and `fabric_version` in `gradle.properties` (values are listed at https://modmuss50.me/fabric.html)
and rebuild. The `depends.minecraft` range in `fabric.mod.json` is set to `>=1.21 <=1.21.11`.

## Credits

Ghost Client is built on the open-source **oyvey-ported** base
(Kosher client base ported to modern Minecraft by [@cattyngmd](https://github.com/cattyngmd)).
Original base authors: 3arthqu4ke, alpha432, cattyn. Licensed under MIT.
