# Skyloom

## Using it

* Press **I** to open the picker. The bind is a normal Minecraft keybind, so it can be changed in
  Options -> Controls -> Skyloom, or with the `Key` button in the picker itself.
* **Browse** lists the skies available for download. Hit **Get** on a card and it lands in your
  library, ready to use. **Remove** takes it back off the disk.
* **My skies** is what you have installed. Click one to turn it on, click it again or hit
  **Turn off** to go back to the vanilla sky. The choice survives restarts.
* **Settings** holds the clean up switches, see below.

## Making skies

`tools/` has everything needed to build a collection from scratch, and needs nothing but a JDK.

**One panorama into a sheet.** Any equirectangular image, which is what free HDRIs and 360 photos
come as:

```
java tools/PanoramaToSkybox.java panorama.jpg sky.png 1536
```

The third argument is pixels per cube face, so 1536 writes a 4608x3072 sheet. A fourth argument
rotates the result in degrees, for when the panorama's centre is not where north should be.

**A whole collection at once.** Poly Haven publishes hundreds of sky HDRIs under CC0, which means
they can be redistributed freely, no permission and no attribution required:

```
python3 tools/build_skies.py --count 40 \
    --base-url https://github.com/you/skyloom-skies/releases/download/v1 \
    --thumb-url https://raw.githubusercontent.com/you/skyloom-skies/main/thumbs
```

That downloads, converts, packs one zip per sky, cuts a thumbnail for each and writes a finished
`catalog.json`. Only panoramas without terrain are used, so the lower faces stay sky. Reckon on
roughly 12 MB per sky, so a set of 40 lands near 500 MB.

Upload `out/zips/*` as release assets, commit `out/thumbs/*`, serve `out/catalog.json`.

**A word on other people's art.** Sky packs from Planet Minecraft and the like are made by
individual artists and nearly all of them forbid reuploading. Redistributing one through the
catalog is the fast way to a takedown, and fan art of a licensed character is worse. CC0 sources
avoid the problem entirely.

## Hosting the catalog

The Browse tab reads a json file over https. Its address lives in `config/skyloom.json` as
`catalogUrl`, so it can be repointed without a new build.

A free setup that holds up: put `catalog.json` in a public GitHub repository and read it through
`raw.githubusercontent.com`, but upload the sky archives as **release assets** rather than repo
files. Release downloads have no size cap or bandwidth throttling, plain repo files do.

```json
{
  "formatVersion": 1,
  "skies": [
    {
      "id": "absol",
      "name": "Absol",
      "category": "Anime",
      "description": "Shown as the subtitle while the card is hovered.",
      "thumbnail": "https://raw.githubusercontent.com/you/skyloom-skies/main/thumbs/absol.png",
      "download": "https://github.com/you/skyloom-skies/releases/download/v1/absol.zip",
      "size": 50331648
    }
  ]
}
```

| Field | Needed | Notes |
| --- | --- | --- |
| `id` | yes | Lowercase, dashes. Also the file name on disk, so keep it stable |
| `download` | yes | Must be https and must serve a zip |
| `name`, `category`, `description` | no | What the card shows |
| `thumbnail` | no | A small https image, cached after the first fetch. Without it the card stays blank |
| `size` | no | Bytes, only used for the label and the progress bar |

Each archive is a sky pack in one of the formats below. It is stored as it came down and read
straight out of the zip, nothing is ever unpacked onto the disk.

## Pack formats

**An OptiFine or MCPatcher custom sky pack.** Drop it in as it is. Every `sky/world0/skyN.properties`
becomes a layer, in number order, and `source`, `blend`, `rotate`, `speed`, `axis`, `startFadeIn`,
`endFadeIn`, `startFadeOut`, `endFadeOut` and `weather` are read from it. `days`, `daysLoop`,
`biomes` and `heights` are ignored, those layers simply always show. Leaving out `startFadeOut` is
fine, the fade out then mirrors the fade in.

**A collection of several packs.** One archive may hold many. Every sky definition inside becomes
its own entry, named after the folder it sits in, so identical file names between packs do not
clash:

```
collection.zip
├── Absol/assets/minecraft/optifine/sky/world0/...   -> "Absol"
└── Nebula/assets/minecraft/optifine/sky/world0/...  -> "Nebula"
```

**Skyloom's own `sky.json`.**

```json
{
  "name": "My Sky",
  "category": "Custom",
  "layers": [
    {
      "texture": "sheet.png",
      "blend": "replace",
      "rotate": true,
      "speed": 1.0,
      "axis": [0.0, 0.0, 1.0],
      "startFadeIn": "20:00",
      "endFadeIn": "22:00",
      "startFadeOut": "04:00",
      "endFadeOut": "06:00",
      "weather": ["clear", "rain"]
    }
  ]
}
```

**Bare sheets.** An archive holding nothing but `.png` files turns every one of them into its own
opaque sky.

Anything dropped into `.minecraft/skyloom/skies` by hand is picked up as well, same formats. Use
**Rescan** in the picker afterwards.

## Sheet layout

The same layout OptiFine and MCPatcher use, three columns by two rows, so existing textures work
unchanged:

```
+--------+--------+--------+
| bottom |  top   |  east  |
+--------+--------+--------+
| south  |  west  |  north |
+--------+--------+--------+
```

Any size works as long as the sheet is 3:2. A face covers 90 degrees of view, so 1536 px per face
(a 4608x3072 sheet) is roughly one to one on a 1080p screen and 2560 px per face suits 1440p and
above. Only the sky being rendered is held in video memory, so a large library costs no more than
a single pack.

## Blend modes

`replace` (opaque, the usual choice for a full sky), `alpha`, `add`, `subtract`, `multiply`,
`dodge`, `burn`, `screen` and `overlay`. Layers draw in order, so a `replace` base with an `add`
layer of clouds or stars on top works the way it does in OptiFine.

## Settings

Stored in `config/skyloom.json`.

| Config key | Tab label | What it does |
| --- | --- | --- |
| `hideSun` | Sun | Removes the vanilla sun disc |
| `hideMoon` | Moon | Removes the moon and its phases |
| `hideStars` | Stars | Removes the vanilla star field |
| `hideSunrise` | Sunrise glow | Removes the orange band at dawn and dusk |
| `hideClouds` | Clouds | Removes every cloud layer |
| `hideWeather` | Rain and snow | Removes falling weather, the sound stays |
| `brightness` | Brightness | Dims the picked sky |
| `rotate` | Turn with the day | Whether skies that ask for it turn with the sun |
| `speed` | Turn speed | Multiplier on that rotation |
| `overworldOnly` | Overworld only | Keeps the custom sky out of other dimensions |
| `catalogUrl` | - | Where the Browse tab fetches its list from |
| `accent` | - | Accent colour of the picker, as 0xRRGGBB |

The switches read as "this is shown", so turning **Sun** off is what sets `hideSun`. They work on
their own and do not need a sky to be picked.

## Where it draws

The skybox is drawn inside vanillas sky pass, after the sky colour and before sun, moon and stars,
which is why the sun still rises over it unless **Sun** is off. It follows the vanilla sky rules,
so it does not show in the Nether, and in the End the End sky stays.
