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

### Utility
- **Fullbright** — see in the dark (restores your gamma on disable).
- **Zoom** — smooth toggle zoom.
- **ToggleSprint** — auto-sprint while moving.

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
