#!/usr/bin/env python3
"""Generates seamless skybox sheets in the OptiFine 3x2 layout.

The face matrices mirror me.alpha432.oyvey.features.sky.render.SkyFace so the generated
sheet lines up with what the mod draws.
"""
import math
import os
import random
import struct
import sys
import zlib

SIZE = 512
COLS, ROWS = 3, 2


def mat_mul(a, b):
    return tuple(tuple(sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)) for i in range(3))


def rot_x(t):
    c, s = math.cos(t), math.sin(t)
    return ((1, 0, 0), (0, c, -s), (0, s, c))


def rot_y(t):
    c, s = math.cos(t), math.sin(t)
    return ((c, 0, s), (0, 1, 0), (-s, 0, c))


def rot_z(t):
    c, s = math.cos(t), math.sin(t)
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


HALF = math.pi / 2.0
PI = math.pi

# name, matrix, column, row  -- identical order/placement to SkyFace
FACES = [
    ("bottom", rot_y(HALF), 0, 0),
    ("top", mat_mul(rot_x(PI), rot_y(-HALF)), 1, 0),
    ("east", mat_mul(rot_x(HALF), rot_z(HALF)), 2, 0),
    ("south", mat_mul(rot_x(HALF), rot_z(PI)), 0, 1),
    ("west", mat_mul(rot_x(HALF), rot_z(-HALF)), 1, 1),
    ("north", rot_x(HALF), 2, 1),
]


def apply(m, v):
    return (m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2])


def apply_t(m, v):
    return (m[0][0] * v[0] + m[1][0] * v[1] + m[2][0] * v[2],
            m[0][1] * v[0] + m[1][1] * v[1] + m[2][1] * v[2],
            m[0][2] * v[0] + m[1][2] * v[1] + m[2][2] * v[2])


# ---------------------------------------------------------------------------------------
# value noise on a wrapping lattice, so every face samples the same continuous field
# ---------------------------------------------------------------------------------------
LATTICE = 64


def make_lattice(seed):
    rng = random.Random(seed)
    return [rng.random() for _ in range(LATTICE ** 3)]


def noise(lat, x, y, z):
    xi, yi, zi = int(math.floor(x)), int(math.floor(y)), int(math.floor(z))
    xf, yf, zf = x - xi, y - yi, z - zi
    u = xf * xf * (3.0 - 2.0 * xf)
    v = yf * yf * (3.0 - 2.0 * yf)
    w = zf * zf * (3.0 - 2.0 * zf)
    x0, y0, z0 = xi % LATTICE, yi % LATTICE, zi % LATTICE
    x1, y1, z1 = (x0 + 1) % LATTICE, (y0 + 1) % LATTICE, (z0 + 1) % LATTICE
    p000 = lat[z0 + LATTICE * (y0 + LATTICE * x0)]
    p100 = lat[z0 + LATTICE * (y0 + LATTICE * x1)]
    p010 = lat[z0 + LATTICE * (y1 + LATTICE * x0)]
    p110 = lat[z0 + LATTICE * (y1 + LATTICE * x1)]
    p001 = lat[z1 + LATTICE * (y0 + LATTICE * x0)]
    p101 = lat[z1 + LATTICE * (y0 + LATTICE * x1)]
    p011 = lat[z1 + LATTICE * (y1 + LATTICE * x0)]
    p111 = lat[z1 + LATTICE * (y1 + LATTICE * x1)]
    a = p000 + (p100 - p000) * u
    b = p010 + (p110 - p010) * u
    c = p001 + (p101 - p001) * u
    d = p011 + (p111 - p011) * u
    e = a + (b - a) * v
    f = c + (d - c) * v
    return e + (f - e) * w


def fbm(lat, x, y, z, octaves=4, freq=2.0, gain=0.5):
    total, amp, norm = 0.0, 1.0, 0.0
    for _ in range(octaves):
        total += amp * noise(lat, x * freq, y * freq, z * freq)
        norm += amp
        amp *= gain
        freq *= 2.0
    return total / norm


def clamp01(value):
    return 0.0 if value < 0.0 else (1.0 if value > 1.0 else value)


def smoothstep(edge0, edge1, x):
    t = clamp01((x - edge0) / (edge1 - edge0))
    return t * t * (3.0 - 2.0 * t)


# ---------------------------------------------------------------------------------------
# the three skies
# ---------------------------------------------------------------------------------------

def sky_midnight(lat, d):
    up = d[1]
    base = 0.5 + 0.5 * up
    r = 0.016 + 0.030 * base
    g = 0.022 + 0.042 * base
    b = 0.055 + 0.110 * base
    # a milky way band around a tilted great circle
    band = abs(d[0] * 0.42 + d[1] * 0.78 + d[2] * 0.46)
    density = (1.0 - smoothstep(0.0, 0.34, band)) * fbm(lat, d[0] * 3.0 + 11.0, d[1] * 3.0, d[2] * 3.0, 5)
    density = max(0.0, density - 0.18) * 1.5
    r += density * 0.30
    g += density * 0.33
    b += density * 0.46
    return r, g, b


def sky_nebula(lat, d):
    r, g, b = 0.014, 0.010, 0.030
    warm = max(0.0, fbm(lat, d[0] * 1.7 + 3.0, d[1] * 1.7 - 5.0, d[2] * 1.7, 5) - 0.42) * 2.6
    cool = max(0.0, fbm(lat, d[0] * 2.3 - 9.0, d[1] * 2.3 + 2.0, d[2] * 2.3, 5) - 0.40) * 2.4
    dust = max(0.0, fbm(lat, d[0] * 5.0, d[1] * 5.0 + 17.0, d[2] * 5.0, 4) - 0.50) * 1.8
    r += warm * 0.62 + cool * 0.10 + dust * 0.20
    g += warm * 0.16 + cool * 0.22 + dust * 0.08
    b += warm * 0.44 + cool * 0.70 + dust * 0.26
    return r, g, b


def sky_aurora(lat, d):
    up = d[1]
    base = 0.5 + 0.5 * up
    r = 0.010 + 0.020 * base
    g = 0.020 + 0.038 * base
    b = 0.040 + 0.080 * base
    # curtains: vertical streaks that only live above the horizon
    horizon = smoothstep(-0.10, 0.55, up) * (1.0 - smoothstep(0.55, 1.0, up) * 0.55)
    streak = fbm(lat, d[0] * 2.6, d[1] * 0.7 + 4.0, d[2] * 2.6, 4)
    ripple = fbm(lat, d[0] * 7.0 + 21.0, d[1] * 1.6, d[2] * 7.0, 3)
    curtain = max(0.0, streak * 0.75 + ripple * 0.35 - 0.52) * 3.2 * horizon
    r += curtain * 0.10
    g += curtain * 0.72
    b += curtain * 0.40
    return r, g, b


SKIES = {
    "midnight": (sky_midnight, 1337, 1400, 0.55),
    "nebula": (sky_nebula, 90210, 2200, 0.75),
    "aurora": (sky_aurora, 4242, 1100, 0.5),
}


# ---------------------------------------------------------------------------------------

def write_png(path, width, height, pixels):
    raw = bytearray()
    stride = width * 3
    for y in range(height):
        raw.append(0)
        raw += pixels[y * stride:(y + 1) * stride]

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    with open(path, "wb") as handle:
        handle.write(b"\x89PNG\r\n\x1a\n")
        handle.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)))
        handle.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        handle.write(chunk(b"IEND", b""))


def generate(name, out_path):
    shade, seed, star_count, star_scale = SKIES[name]
    lat = make_lattice(seed)
    width, height = COLS * SIZE, ROWS * SIZE
    pixels = bytearray(width * height * 3)
    floats = [0.0] * (width * height * 3)

    for face_name, matrix, col, row in FACES:
        ox, oy = col * SIZE, row * SIZE
        for py in range(SIZE):
            t = (py + 0.5) / SIZE
            lz = (2.0 * t - 1.0)
            for px in range(SIZE):
                s = (px + 0.5) / SIZE
                lx = (2.0 * s - 1.0)
                d = apply(matrix, (lx, -1.0, lz))
                length = math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
                d = (d[0] / length, d[1] / length, d[2] / length)
                r, g, b = shade(lat, d)
                index = ((oy + py) * width + (ox + px)) * 3
                floats[index] = r
                floats[index + 1] = g
                floats[index + 2] = b
        print("  %s/%s" % (name, face_name), file=sys.stderr)

    splat_stars(floats, width, star_count, star_scale, seed)

    for i, value in enumerate(floats):
        # gentle filmic-ish curve so the faint parts stay visible
        v = value / (1.0 + value)
        pixels[i] = int(clamp01(v ** (1.0 / 1.35)) * 255.0 + 0.5)

    write_png(out_path, width, height, pixels)
    print("wrote %s (%d bytes)" % (out_path, os.path.getsize(out_path)), file=sys.stderr)


def splat_stars(floats, width, count, scale, seed):
    rng = random.Random(seed + 7)
    for _ in range(count):
        # uniform point on the sphere
        z = rng.uniform(-1.0, 1.0)
        phi = rng.uniform(0.0, 2.0 * math.pi)
        radius = math.sqrt(max(0.0, 1.0 - z * z))
        d = (radius * math.cos(phi), z, radius * math.sin(phi))

        brightness = (rng.random() ** 3.2) * 2.4 * scale + 0.08
        size = 0.7 + rng.random() * 1.5
        tint = rng.random()
        cr = 1.0 - 0.22 * max(0.0, 0.5 - tint) * 2.0
        cb = 1.0 - 0.22 * max(0.0, tint - 0.5) * 2.0

        for face_name, matrix, col, row in FACES:
            local = apply_t(matrix, d)
            if local[1] >= -1e-6:
                continue
            depth = -local[1]
            u = local[0] / depth
            v = local[2] / depth
            if abs(u) > 1.0 or abs(v) > 1.0:
                continue
            cx = (u * 0.5 + 0.5) * SIZE - 0.5 + col * SIZE
            cy = (v * 0.5 + 0.5) * SIZE - 0.5 + row * SIZE
            reach = int(size * 2.0) + 1
            for py in range(int(cy) - reach, int(cy) + reach + 1):
                if py < row * SIZE or py >= (row + 1) * SIZE:
                    continue
                for px in range(int(cx) - reach, int(cx) + reach + 1):
                    if px < col * SIZE or px >= (col + 1) * SIZE:
                        continue
                    dx, dy = px - cx, py - cy
                    falloff = math.exp(-(dx * dx + dy * dy) / (2.0 * size * size * 0.30))
                    if falloff < 0.004:
                        continue
                    index = (py * width + px) * 3
                    floats[index] += brightness * falloff * cr
                    floats[index + 1] += brightness * falloff
                    floats[index + 2] += brightness * falloff * cb
            break


if __name__ == "__main__":
    target = sys.argv[1]
    os.makedirs(target, exist_ok=True)
    for sky in sys.argv[2:] or list(SKIES):
        generate(sky, os.path.join(target, sky + ".png"))
