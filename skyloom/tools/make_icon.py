#!/usr/bin/env python3
"""Draws the Skyloom icon: a night sky with the skybox cube standing in it.

Pure standard library, so it runs anywhere a JDK-free machine would. The image is rendered at
three times the target size and boxed down afterwards, which is what gives the cube edges their
smooth diagonals.

    python3 make_icon.py out/icon-512.png 512
"""
import math
import pathlib
import random
import struct
import sys
import zlib

SUPERSAMPLE = 3

# the accent the menu uses, plus the two ends of the sky behind it
ACCENT = (0x4C, 0x6F, 0xFF)
SKY_TOP = (0x07, 0x0A, 0x18)
SKY_BOTTOM = (0x24, 0x2B, 0x63)


def mix(a, b, t):
    t = 0.0 if t < 0.0 else 1.0 if t > 1.0 else t
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def over(base, colour, alpha):
    if alpha <= 0.0:
        return base
    alpha = 1.0 if alpha > 1.0 else alpha
    return tuple(round(b + (c - b) * alpha) for b, c in zip(base, colour))


def cube_points(cx, cy, half_width, edge):
    """The seven corners of an isometric cube, clockwise from the top."""
    a = half_width * 0.5
    return {
        "A": (cx, cy - a - edge / 2),
        "B": (cx + half_width, cy - edge / 2),
        "C": (cx, cy + a - edge / 2),
        "D": (cx - half_width, cy - edge / 2),
        "E": (cx - half_width, cy + edge / 2),
        "F": (cx, cy + a + edge / 2),
        "G": (cx + half_width, cy + edge / 2),
    }


def inside(polygon, x, y):
    """Convex polygons only, which is all a cube face ever is."""
    sign = 0
    count = len(polygon)
    for i in range(count):
        x0, y0 = polygon[i]
        x1, y1 = polygon[(i + 1) % count]
        cross = (x1 - x0) * (y - y0) - (y1 - y0) * (x - x0)
        if cross > 0:
            if sign < 0:
                return False
            sign = 1
        elif cross < 0:
            if sign > 0:
                return False
            sign = -1
    return True


def distance_to_segment(px, py, x0, y0, x1, y1):
    dx, dy = x1 - x0, y1 - y0
    length = dx * dx + dy * dy
    t = 0.0 if length == 0 else ((px - x0) * dx + (py - y0) * dy) / length
    t = 0.0 if t < 0.0 else 1.0 if t > 1.0 else t
    return math.hypot(px - (x0 + dx * t), py - (y0 + dy * t))


def render(size):
    big = size * SUPERSAMPLE
    pixels = bytearray(big * big * 3)

    centre_x = centre_y = big / 2.0
    glow_radius = big * 0.42

    # the sky, plus a soft accent glow sitting behind where the cube will stand
    for y in range(big):
        row = mix(SKY_TOP, SKY_BOTTOM, (y / (big - 1)) ** 0.85)
        base = y * big * 3
        for x in range(big):
            distance = math.hypot(x - centre_x, y - centre_y)
            glow = max(0.0, 1.0 - distance / glow_radius) ** 2.2
            colour = over(row, ACCENT, glow * 0.30)
            offset = base + x * 3
            pixels[offset] = colour[0]
            pixels[offset + 1] = colour[1]
            pixels[offset + 2] = colour[2]

    draw_stars(pixels, big)
    draw_cube(pixels, big)
    return downsample(pixels, big, size)


def draw_stars(pixels, big):
    rng = random.Random(7)
    for _ in range(150):
        sx = rng.uniform(0, big)
        sy = rng.uniform(0, big)
        # keep the middle clear so the cube stays the only thing you read first
        if math.hypot(sx - big / 2, sy - big / 2) < big * 0.24:
            continue
        radius = rng.uniform(big * 0.0015, big * 0.0045)
        brightness = rng.uniform(0.35, 1.0)
        tint = mix((0xFF, 0xFF, 0xFF), (0xBF, 0xCE, 0xFF), rng.random())
        reach = radius * 3.0
        for y in range(max(0, int(sy - reach)), min(big, int(sy + reach) + 1)):
            for x in range(max(0, int(sx - reach)), min(big, int(sx + reach) + 1)):
                distance = math.hypot(x + 0.5 - sx, y + 0.5 - sy)
                alpha = max(0.0, 1.0 - distance / reach) ** 2.5 * brightness
                if alpha <= 0.004:
                    continue
                offset = (y * big + x) * 3
                colour = over((pixels[offset], pixels[offset + 1], pixels[offset + 2]), tint, alpha)
                pixels[offset] = colour[0]
                pixels[offset + 1] = colour[1]
                pixels[offset + 2] = colour[2]


def draw_cube(pixels, big):
    points = cube_points(big / 2.0, big / 2.0, big * 0.245, big * 0.275)
    # one face per time of day, because picking between them is the whole mod
    faces = (
        ((points["A"], points["B"], points["C"], points["D"]),
         (0x9C, 0xB6, 0xFF), (0x5A, 0x79, 0xE8)),   # top, daylight
        ((points["D"], points["C"], points["F"], points["E"]),
         (0x2A, 0x22, 0x58), (0x14, 0x10, 0x33)),   # left, night
        ((points["B"], points["G"], points["F"], points["C"]),
         (0xFF, 0x8A, 0x52), (0xB8, 0x3C, 0x74)),   # right, sunset
    )
    edges = (
        ("A", "B"), ("B", "C"), ("C", "D"), ("D", "A"),
        ("C", "F"), ("D", "E"), ("B", "G"),
        ("E", "F"), ("F", "G"),
    )
    stroke = big * 0.011
    edge_colour = (0xAE, 0xC1, 0xFF)

    face_bounds = []
    for polygon, top_colour, bottom_colour in faces:
        face_ys = [p[1] for p in polygon]
        face_bounds.append((polygon, top_colour, bottom_colour, min(face_ys), max(face_ys)))

    xs = [p[0] for p in points.values()]
    ys = [p[1] for p in points.values()]
    x_from, x_to = int(min(xs) - stroke * 3), int(max(xs) + stroke * 3) + 1
    y_from, y_to = int(min(ys) - stroke * 3), int(max(ys) + stroke * 3) + 1

    segments = [(points[a][0], points[a][1], points[b][0], points[b][1]) for a, b in edges]

    for y in range(max(0, y_from), min(big, y_to)):
        for x in range(max(0, x_from), min(big, x_to)):
            px, py = x + 0.5, y + 0.5
            offset = (y * big + x) * 3
            colour = (pixels[offset], pixels[offset + 1], pixels[offset + 2])

            for polygon, top_colour, bottom_colour, y_min, y_max in face_bounds:
                if inside(polygon, px, py):
                    colour = mix(top_colour, bottom_colour, (py - y_min) / max(1e-6, y_max - y_min))
                    break

            nearest = min(distance_to_segment(px, py, *segment) for segment in segments)
            if nearest < stroke * 2.5:
                line = max(0.0, 1.0 - max(0.0, nearest - stroke * 0.5) / stroke)
                halo = max(0.0, 1.0 - nearest / (stroke * 2.5)) ** 2
                colour = over(colour, edge_colour, halo * 0.22)
                colour = over(colour, edge_colour, line)

            pixels[offset] = colour[0]
            pixels[offset + 1] = colour[1]
            pixels[offset + 2] = colour[2]


def downsample(pixels, big, size):
    out = bytearray(size * size * 3)
    factor = SUPERSAMPLE
    weight = factor * factor
    for y in range(size):
        for x in range(size):
            red = green = blue = 0
            for sy in range(y * factor, y * factor + factor):
                row = sy * big * 3
                for sx in range(x * factor, x * factor + factor):
                    offset = row + sx * 3
                    red += pixels[offset]
                    green += pixels[offset + 1]
                    blue += pixels[offset + 2]
            offset = (y * size + x) * 3
            out[offset] = red // weight
            out[offset + 1] = green // weight
            out[offset + 2] = blue // weight
    return out


def write_png(path, size, pixels):
    raw = bytearray()
    stride = size * 3
    for y in range(size):
        raw.append(0)
        raw += pixels[y * stride:(y + 1) * stride]

    def chunk(tag, body):
        head = struct.pack(">I", len(body)) + tag + body
        return head + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                     + chunk(b"IEND", b""))


def box(pixels, size, target):
    """Plain box filter, only ever used to go from 512 down to the 128 the jar wants."""
    factor = size // target
    out = bytearray(target * target * 3)
    weight = factor * factor
    for y in range(target):
        for x in range(target):
            red = green = blue = 0
            for sy in range(y * factor, y * factor + factor):
                for sx in range(x * factor, x * factor + factor):
                    offset = (sy * size + sx) * 3
                    red += pixels[offset]
                    green += pixels[offset + 1]
                    blue += pixels[offset + 2]
            offset = (y * target + x) * 3
            out[offset] = red // weight
            out[offset + 1] = green // weight
            out[offset + 2] = blue // weight
    return out


def main():
    out = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "icon-512.png")
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 512
    out.parent.mkdir(parents=True, exist_ok=True)

    pixels = render(size)
    write_png(out, size, pixels)
    print(f"wrote {out} ({size}x{size})")

    if size % 128 == 0 and size != 128:
        small = out.with_name(out.name.replace(str(size), "128"))
        write_png(small, 128, box(pixels, size, 128))
        print(f"wrote {small} (128x128)")


if __name__ == "__main__":
    sys.exit(main())
