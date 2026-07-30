#!/usr/bin/env python3
"""Generate the SmoothInv mod icon as 32x32 pixel art, upscaled with nearest neighbour.

Writes the 256px icon shipped in the jar plus a 512px version for Modrinth.
Run with Pillow installed: python3 icon/make_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
MOD_ROOT = HERE.parent

S = 32  # logical pixel-art canvas

BG_TOP = (34, 38, 47)
BG_BOTTOM = (24, 26, 33)
SLOT_FILL = (139, 139, 139)
SLOT_SHADOW = (58, 58, 58)
SLOT_LIGHT = (232, 232, 232)
BOLT_FILL = (255, 217, 61)
BOLT_SHADE = (240, 165, 0)
BOLT_OUTLINE = (26, 22, 14)

img = Image.new("RGBA", (S, S), BG_BOTTOM)
d = ImageDraw.Draw(img)

# Vertical gradient background, one flat band per pixel row.
for y in range(S):
    t = y / (S - 1)
    col = tuple(round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM))
    d.line([(0, y), (S - 1, y)], fill=col)


def slot(x0, y0, size=11):
    """Draw a vanilla-style inset inventory slot: dark top-left, light bottom-right."""
    x1, y1 = x0 + size - 1, y0 + size - 1
    d.rectangle([x0, y0, x1, y1], fill=SLOT_FILL)
    d.line([(x0, y0), (x1 - 1, y0)], fill=SLOT_SHADOW)  # top
    d.line([(x0, y0), (x0, y1 - 1)], fill=SLOT_SHADOW)  # left
    d.line([(x0 + 1, y1), (x1, y1)], fill=SLOT_LIGHT)   # bottom
    d.line([(x1, y0 + 1), (x1, y1)], fill=SLOT_LIGHT)   # right


# 2x2 slot grid: 11px slots with a 2px gap, 4px margin all round.
for sy in (4, 17):
    for sx in (4, 17):
        slot(sx, sy)

# Lightning bolt, top-right to bottom-left.
BOLT = [(19, 3), (11, 17), (15, 17), (12, 29), (21, 13), (16, 13)]

# 1px outline: same polygon nudged in 8 directions underneath the fill.
for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, -1), (-1, 1), (1, 1)):
    d.polygon([(x + dx, y + dy) for x, y in BOLT], fill=BOLT_OUTLINE)

d.polygon(BOLT, fill=BOLT_FILL)

# Two-tone shading: darken the bolt's lower half only where the bolt already is.
shade = Image.new("RGBA", (S, S), (0, 0, 0, 0))
sd = ImageDraw.Draw(shade)
sd.polygon(BOLT, fill=BOLT_SHADE)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rectangle([0, 17, S - 1, S - 1], fill=255)
img.paste(shade, (0, 0), Image.composite(mask, Image.new("L", (S, S), 0), shade.split()[3]))

img.resize((512, 512), Image.NEAREST).save(HERE / "smoothinv-icon-512.png")

jar_icon = MOD_ROOT / "src/main/resources/assets/smoothinv/icon.png"
jar_icon.parent.mkdir(parents=True, exist_ok=True)
img.resize((256, 256), Image.NEAREST).save(jar_icon)

# Preview strip so the icon can be judged at the sizes Modrinth actually renders.
sizes = [256, 128, 96, 64, 32]
pad = 16
strip = Image.new("RGBA", (sum(sizes) + pad * (len(sizes) + 1), 256 + pad * 2), (18, 19, 23, 255))
x = pad
for s in sizes:
    strip.paste(img.resize((s, s), Image.NEAREST), (x, pad + (256 - s) // 2))
    x += s + pad
strip.save(HERE / "smoothinv-icon-preview.png")

print(f"wrote {jar_icon} and {HERE / 'smoothinv-icon-512.png'}")
