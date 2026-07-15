// Deterministic pixel-art renderer: the model describes a texture as a
// palette (single characters -> colors) plus one string per pixel row; the
// server turns that into a real PNG. LLMs cannot emit binary data — this is
// the bridge.
import { PNG } from 'pngjs';

const SIZES = new Set([16, 32]);

function parseColor(value) {
  if (value === 'transparent') return [0, 0, 0, 0];
  const m = String(value).trim().match(/^#?([0-9a-fA-F]{6})([0-9a-fA-F]{2})?$/);
  if (!m) return null;
  const n = parseInt(m[1], 16);
  const alpha = m[2] !== undefined ? parseInt(m[2], 16) : 255;
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff, alpha];
}

/**
 * @returns {{png: Buffer}|{error: string}}
 */
export function renderTexture({ size, palette, rows }) {
  if (!SIZES.has(size)) return { error: `size must be one of ${[...SIZES].join(', ')}` };
  if (!palette || typeof palette !== 'object') return { error: 'palette must be an object mapping single characters to "#rrggbb", "#rrggbbaa" or "transparent"' };
  if (!Array.isArray(rows) || rows.length !== size) return { error: `rows must be an array of exactly ${size} strings (got ${Array.isArray(rows) ? rows.length : typeof rows})` };

  const colors = new Map();
  for (const [ch, val] of Object.entries(palette)) {
    if (ch.length !== 1) return { error: `palette key "${ch}" must be a single character` };
    const rgba = parseColor(val);
    if (!rgba) return { error: `palette["${ch}"] = "${val}" is not a valid color` };
    colors.set(ch, rgba);
  }

  const png = new PNG({ width: size, height: size });
  for (let y = 0; y < size; y++) {
    const row = rows[y];
    if (typeof row !== 'string' || row.length !== size) {
      return { error: `row ${y} must be a string of exactly ${size} characters (got ${typeof row === 'string' ? row.length : typeof row})` };
    }
    for (let x = 0; x < size; x++) {
      const rgba = colors.get(row[x]);
      if (!rgba) return { error: `row ${y}, column ${x}: character "${row[x]}" is not in the palette` };
      const idx = (y * size + x) * 4;
      png.data[idx] = rgba[0];
      png.data[idx + 1] = rgba[1];
      png.data[idx + 2] = rgba[2];
      png.data[idx + 3] = rgba[3];
    }
  }
  return { png: PNG.sync.write(png) };
}
