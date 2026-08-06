# Skyloom

Replaces the vanilla sky with a skybox of your choice, picked from an in game menu that can be
opened at any time. Nothing needs to be reloaded and no resource pack has to be swapped, the sky
changes the moment you click one.

## Using it

* Press **I** to open the picker. The bind lives on a normal Minecraft keybind, so it can be changed in
  Options -> Controls -> Skyloom, or with the `Key` button in the picker itself.
* Click a sky to turn it on, click it again or hit **Turn off** to go back to the vanilla sky.
* The choice is stored in `modules.json` and comes back after a restart.
* Categories are on the left, the search box in the top right filters by name and category.
* The **Settings** tab next to them holds the clean up switches, see below.

## Settings

The **Settings** tab of the picker holds everything else. They are stored in `config/skyloom.json`.

| Config key | Tab label | What it does |
| --- | --- | --- |
| `hideSun` | Sun | Removes the vanilla sun disc |
| `hideMoon` | Moon | Removes the moon and its phases |
| `hideStars` | Stars | Removes the vanilla star field |
| `hideSunrise` | Sunrise glow | Removes the orange band at dawn and dusk |
| `hideClouds` | Clouds | Removes every cloud layer |
| `hideWeather` | Rain and snow | Removes falling weather, the sound stays |
| `brightness` | Brightness | Dims the picked sky, `1` is the texture as authored |
| `rotate` | Turn with the day | Whether skies that ask for it turn with the sun |
| `speed` | Turn speed | Multiplier on that rotation |
| `overworldOnly` | Overworld only | Keeps the custom sky out of other dimensions |

The switches in the tab read as "this is shown", so turning **Sun** off is what sets `hideSun`.
They work on their own and do not need a sky to be picked, so the vanilla sky can be cleaned up
without replacing it.

## Shipping skies inside the jar

Only needed to hand skies to everyone who downloads the mod. For your own use the folder
below is enough:

1. Put the sheet in `src/main/resources/assets/skyloom/textures/sky/<name>.png`.
2. Add an entry to `src/main/resources/assets/skyloom/skies/index.json`.

```json
{
  "id": "my-sky",
  "name": "My Sky",
  "category": "Space",
  "description": "Shown as a tooltip in the picker.",
  "layers": [
    { "texture": "skyloom:textures/sky/my-sky.png", "blend": "replace", "rotate": true, "speed": 1.0 }
  ]
}
```

Keep ids lowercase and use dashes. Bundled sheets grow the jar, so keep them few and modest.

## Adding skies without rebuilding

Anything inside `.minecraft/skyloom/skies` is picked up as well, as a folder or as a zip. Hit
**Reload** in the picker after dropping something in. Three shapes are understood:

**1. A folder or zip with a `sky.json`**

```
my-sky/
  sky.json
  sheet.png
```

```json
{
  "name": "My Sky",
  "category": "Custom",
  "layers": [
    {
      "texture": "sheet.png",
      "blend": "add",
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

**2. An OptiFine / MCPatcher custom sky pack**

Drop the resource pack in as it is. Every `sky/world0/skyN.properties` becomes a layer, in number
order, and `source`, `blend`, `rotate`, `speed`, `axis`, `startFadeIn`, `endFadeIn`, `startFadeOut`,
`endFadeOut` and `weather` are read from it. `days`, `daysLoop`, `biomes` and `heights` are ignored,
those layers simply always show.

**3. A folder holding nothing but a sheet**

A single `.png` in the folder root is enough, it becomes an opaque one layer sky.

## Sheet layout

The same layout OptiFine and MCPatcher use, three columns by two rows, so any existing custom sky
texture works unchanged:

```
+--------+--------+--------+
| bottom |  top   |  east  |
+--------+--------+--------+
| south  |  west  |  north |
+--------+--------+--------+
```

Any size works as long as the sheet is 3:2. A face covers 90 degrees of view, so 1536 px per face (a 4608x3072 sheet) is roughly one to
one on a 1080p screen and 2560 px per face suits 1440p and above. Only the sky being rendered
is held in video memory, so a large collection costs no more than a single pack.

## Blend modes

`replace` (opaque, the usual choice for a full sky), `alpha`, `add`, `subtract`, `multiply`,
`dodge`, `burn`, `screen` and `overlay`. Layers draw in order, so a `replace` base with an `add`
layer of clouds or stars on top works the way it does in OptiFine.

## Where it draws

The skybox is drawn inside vanillas sky pass, after the sky colour and before sun, moon and stars,
which is why the sun still rises over it unless `HideSun` is on. It follows the vanilla sky rules,
so it does not show in the Nether, and in the End the End sky stays.
