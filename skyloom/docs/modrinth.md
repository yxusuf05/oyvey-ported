# Modrinth listing

Everything the project page needs, ready to paste. Keep this in sync when the mod changes.

## Summary

The short line under the title. Modrinth allows 256 characters.

> Change the sky from an in-game menu. Browse and download skies without leaving Minecraft, or load your own OptiFine sky packs. Switch any time, no restart, no resource pack juggling.

## Categories

`decoration`, `utility`

## Environment

Client side only. Server side: unsupported.

## Links

- Issues / source: leave empty until there is a public repository for the mod itself.
- Wiki: not needed.

## Description

Paste the block below into the description editor.

---

# Skyloom

Press one key, pick a sky, keep playing. No resource pack shuffling, no restart, no config files.

![the picker](REPLACE_WITH_SCREENSHOT_URL)

## What it does

Minecraft's sky is the same blue box in every world you ever load. Skyloom puts a menu in front of
it. Open it with **I**, pick a sky from a list, and the world above your head changes on the spot.
Change your mind ten seconds later and pick another one. Nothing reloads, nothing stutters.

## Getting skies

**Browse them in game.** The Browse tab lists skies you can download with one click. They land in
your My skies tab as soon as the download finishes, and they stay there. No browser, no unzipping,
no dragging files into folders.

**Or bring your own.** Skyloom reads OptiFine and MCPatcher custom sky packs, the format that
thousands of skies have already been made in over the last decade. Drop the zip into
`.minecraft/skyloom/skies` and hit Rescan. It reads `.properties` files, fade times, blend modes,
weather conditions and rotation exactly the way OptiFine does, so packs made for OptiFine work
without being converted.

## The menu

- **My skies** — everything installed, with a preview of each one. Click to wear it, click Turn off
  to go back to vanilla.
- **Browse** — the download list, with a progress bar and a badge on anything already installed.
- **Settings** — switches for the sun, the moon, the stars, the sunrise glow, the clouds and the
  weather. Turn off whatever gets in the way of the sky you picked. There is also a brightness
  slider, a rotation toggle with a speed control, and an option to keep custom skies in the
  Overworld only. Every control takes effect the moment you touch it.

My skies and Browse both have a search box and category filters, so a hundred installed skies stay
findable.

## Compatibility

- **Sodium** — tested, works.
- **Iris** — tested without a shaderpack, works. With a shaderpack enabled the shaderpack draws
  the sky itself, so what you see is up to the shaderpack rather than up to Skyloom. That is how
  shaderpacks work with every sky mod, not something Skyloom can override.
- **OptiFine** — not applicable, OptiFine does not run on Fabric. Skyloom reads OptiFine's sky
  format, which is the part you actually want.
- Requires **Fabric API**.

## About the downloads

The Browse tab fetches a list of skies from a file the mod author hosts, and downloads a sky only
when you click it. Nothing is downloaded in the background and nothing is downloaded on startup.
Downloads are HTTPS only and are checked to be real zip archives before they are kept.

You can point the mod at a different list by editing `catalogUrl` in `config/skyloom.json`, so a
server community or a modpack can host its own collection.

## Credits

The skies offered in the Browse tab are converted from panoramas published by
[Poly Haven](https://polyhaven.com) under CC0.

Skyloom itself is MIT licensed.
