#!/usr/bin/env python3
"""Builds a whole Skyloom sky collection from Poly Haven's CC0 sky HDRIs.

Poly Haven publishes everything under CC0, so the results can be redistributed without
permission or attribution. For each sky this downloads the tonemapped panorama, converts it to
a Skyloom sheet with PanoramaToSkybox.java, packs it into a zip and finally writes a catalog.json
ready to be served to the Browse tab.

    python3 build_skies.py --count 40 --out ./out \
        --base-url https://github.com/you/skyloom-skies/releases/download/v1 \
        --thumb-url https://raw.githubusercontent.com/you/skyloom-skies/main/thumbs

Only panoramas without terrain are used by default, so the lower cube faces stay sky.

Needs a JDK on the PATH and nothing else. Upload out/zips/* as release assets, out/thumbs/* into
the repository, and out/catalog.json wherever catalogUrl points.
"""
import argparse
import json
import pathlib
import random
import re
import shutil
import struct
import subprocess
import sys
import urllib.request
import zipfile
import zlib

API = "https://api.polyhaven.com"
CONVERTER = pathlib.Path(__file__).with_name("PanoramaToSkybox.java")


def fetch(url, binary=False):
    request = urllib.request.Request(url, headers={"User-Agent": "skyloom-build"})
    with urllib.request.urlopen(request, timeout=180) as response:
        return response.read() if binary else json.loads(response.read())


def slug(value):
    value = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return value or "sky"


def pretty(asset_id):
    return " ".join(word.capitalize() for word in asset_id.replace("_", " ").split())


def categorise(asset):
    tags = {tag.lower() for tag in asset.get("tags", [])} | {asset.get("name", "").lower()}
    joined = " ".join(tags)
    for needle, category in (("night", "Night"), ("moon", "Night"), ("star", "Night"),
                             ("sunset", "Sunset"), ("dusk", "Sunset"), ("evening", "Sunset"),
                             ("sunrise", "Sunrise"), ("dawn", "Sunrise"), ("morning", "Sunrise"),
                             ("storm", "Storm"), ("overcast", "Clouds"), ("cloud", "Clouds")):
        if needle in joined:
            return category
    return "Daylight"


def read_png(path):
    """Minimal reader for the 8 bit RGB files the converter writes."""
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a png"
    pos, idat, width, height = 8, bytearray(), 0, 0
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if tag == b"IHDR":
            width, height, depth, colour = struct.unpack(">IIBB", body[:10])
            assert depth == 8 and colour == 2, "expected 8 bit rgb"
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break
        pos += 12 + length

    raw = zlib.decompress(bytes(idat))
    stride = width * 3
    out = bytearray(stride * height)
    previous = bytearray(stride)
    offset = 0
    for y in range(height):
        filter_type = raw[offset]
        offset += 1
        line = bytearray(raw[offset:offset + stride])
        offset += stride
        for i in range(stride):
            a = line[i - 3] if i >= 3 else 0
            b = previous[i]
            c = previous[i - 3] if i >= 3 else 0
            if filter_type == 1:
                line[i] = (line[i] + a) & 255
            elif filter_type == 2:
                line[i] = (line[i] + b) & 255
            elif filter_type == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif filter_type == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out[y * stride:(y + 1) * stride] = line
        previous = line
    return width, height, out


def write_png(path, width, height, pixels):
    raw = bytearray()
    stride = width * 3
    for y in range(height):
        raw.append(0)
        raw += pixels[y * stride:(y + 1) * stride]

    def chunk(tag, body):
        head = struct.pack(">I", len(body)) + tag + body
        return head + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    path.write_bytes(b"\x89PNG\r\n\x1a\n"
                     + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 6))
                     + chunk(b"IEND", b""))


def thumbnail(sheet_path, out_path, size=256):
    """Crops the north face and scales it down, the same view the picker shows."""
    width, height, pixels = read_png(sheet_path)
    face_w, face_h = width // 3, height // 2
    origin_x, origin_y = 2 * face_w, face_h
    out = bytearray(size * size * 3)
    for y in range(size):
        sy = origin_y + min(face_h - 1, y * face_h // size)
        for x in range(size):
            sx = origin_x + min(face_w - 1, x * face_w // size)
            src = (sy * width + sx) * 3
            dst = (y * size + x) * 3
            out[dst:dst + 3] = pixels[src:src + 3]
    write_png(out_path, size, size, out)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=40)
    parser.add_argument("--face", type=int, default=1536, help="pixels per cube face")
    parser.add_argument("--out", default="out")
    parser.add_argument("--base-url", default="https://example.com/releases/download/v1",
                        help="where the zips will end up, used to build catalog.json")
    parser.add_argument("--thumb-url", default=None,
                        help="where the thumbs folder will be served from, defaults to no thumbnails")
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--only", nargs="*", help="specific Poly Haven asset ids instead of a selection")
    parser.add_argument("--include-ground", action="store_true",
                        help="also use panoramas that contain terrain, by default only the puresky ones")
    args = parser.parse_args()

    out = pathlib.Path(args.out)
    zips, thumbs, work = out / "zips", out / "thumbs", out / "work"
    for directory in (zips, thumbs, work):
        directory.mkdir(parents=True, exist_ok=True)

    print("Fetching the Poly Haven sky list ...")
    assets = fetch(f"{API}/assets?t=hdris&c=skies")
    chosen = args.only or sorted(assets)
    if not args.only:
        if not args.include_ground:
            # puresky panoramas have the landscape removed, so the lower faces stay sky
            pure = [asset for asset in chosen if "puresky" in asset]
            if len(pure) >= args.count:
                chosen = pure
            else:
                print(f"only {len(pure)} puresky panoramas exist, filling up with ordinary ones")
                chosen = pure + [asset for asset in chosen if asset not in pure]
        random.Random(args.seed).shuffle(chosen)
        chosen = chosen[:args.count]

    entries = []
    for index, asset_id in enumerate(chosen, 1):
        name = pretty(asset_id)
        print(f"[{index}/{len(chosen)}] {name}")
        try:
            files = fetch(f"{API}/files/{asset_id}")
            url = files.get("tonemapped", {}).get("url")
            if not url:
                print("    no tonemapped panorama, skipping")
                continue

            panorama = work / f"{asset_id}.jpg"
            if not panorama.exists():
                panorama.write_bytes(fetch(url, binary=True))

            sheet = work / f"{asset_id}.png"
            subprocess.run(["java", str(CONVERTER), str(panorama), str(sheet), str(args.face)],
                           check=True, stdout=subprocess.DEVNULL)

            identifier = slug(asset_id)
            category = categorise(assets.get(asset_id, {}))
            manifest = {
                "name": name,
                "category": category,
                "description": f"CC0 sky from Poly Haven by {', '.join(assets.get(asset_id, {}).get('authors', {})) or 'Poly Haven'}.",
                "layers": [{"texture": "sheet.png", "blend": "replace", "rotate": True, "speed": 1.0}],
            }

            archive = zips / f"{identifier}.zip"
            with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zf:
                zf.writestr("sky.json", json.dumps(manifest, indent=2))
                zf.write(sheet, "sheet.png")

            thumbnail(sheet, thumbs / f"{identifier}.png")
            panorama.unlink(missing_ok=True)
            sheet.unlink(missing_ok=True)

            entries.append({
                "id": identifier,
                "name": name,
                "category": category,
                "description": manifest["description"],
                "download": f"{args.base_url.rstrip('/')}/{identifier}.zip",
                "size": archive.stat().st_size,
            })
            if args.thumb_url:
                entries[-1]["thumbnail"] = f"{args.thumb_url.rstrip('/')}/{identifier}.png"
        except Exception as error:  # one broken asset should not stop the batch
            print(f"    failed: {error}")

    (out / "catalog.json").write_text(json.dumps({"formatVersion": 1, "skies": entries}, indent=2))
    shutil.rmtree(work, ignore_errors=True)
    total = sum(entry["size"] for entry in entries)
    print(f"\nDone: {len(entries)} skies, {total / 1e6:.0f} MB of zips")
    print(f"  {zips}/        -> upload as release assets")
    print(f"  {thumbs}/      -> commit into the repository")
    print(f"  {out}/catalog.json -> fix the thumbnail urls, then serve it")


if __name__ == "__main__":
    sys.exit(main())
