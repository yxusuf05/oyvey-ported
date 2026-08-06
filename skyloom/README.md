<div align="center">

# Skyloom

Swap the Minecraft sky from an in-game menu, instantly, without reloading anything.

</div>

## What it does

Press **I** for a picker with every sky you have installed, grouped into categories and
searchable. Click one and the sky changes on the spot: no resource pack swap, no reload, no
restart. Click it again to go back to vanilla.

* Loads **OptiFine and MCPatcher custom sky packs** unchanged, including layers, blend modes,
  time based fades, rotation and weather conditions.
* Also reads a simple `sky.json` format of its own, and plain folders holding a single sheet.
* A **Settings** tab to hide the sun, moon, stars, sunrise glow, clouds or falling weather, for
  a cleaner sky. Those work on their own, no custom sky required.
* Only the sky being rendered is held in video memory, so a collection of a hundred packs costs
  the same as one.

## Installing skies

Drop a pack into `.minecraft/skyloom/skies`, as a folder or a zip, and hit **Reload** in the
picker. See [docs/skies.md](docs/skies.md) for the formats and for the sheet layout.

## Requirements

Minecraft 1.21.11, Fabric Loader 0.18.4 or newer, and Fabric API. Client side only, so it works
on any server.

## License

MIT.
