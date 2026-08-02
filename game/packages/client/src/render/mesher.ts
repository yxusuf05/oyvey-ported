/**
 * Turns the tile grid into renderable geometry.
 *
 * The level is split into 16x16-tile chunks, each one merged `BufferGeometry`. That gives
 * three.js something to frustum-cull at a useful granularity — roughly sixty chunks for a
 * whole floor, of which a handful are ever visible through the fog — while keeping draw
 * calls low enough that no further culling scheme is needed at this scale.
 */

import { BufferAttribute, BufferGeometry } from 'three';
import { CEILING_HEIGHT, FLOOR, TILE_SIZE, type Level } from '@game/shared/levelgen';

export const CHUNK_TILES = 16;

/** Surface ids consumed by the world shader. */
const SURFACE_FLOOR = 0;
const SURFACE_WALL = 1;
const SURFACE_CEILING = 2;

interface Builder {
  positions: number[];
  normals: number[];
  uvs: number[];
  surfaces: number[];
  rooms: number[];
  indices: number[];
}

function newBuilder(): Builder {
  return { positions: [], normals: [], uvs: [], surfaces: [], rooms: [], indices: [] };
}

/** Appends one quad. Vertices are given in counter-clockwise order as seen from the front. */
function quad(
  b: Builder,
  v0: [number, number, number],
  v1: [number, number, number],
  v2: [number, number, number],
  v3: [number, number, number],
  normal: [number, number, number],
  uv: [number, number, number, number],
  surface: number,
  room: number,
): void {
  const base = b.positions.length / 3;
  const verts = [v0, v1, v2, v3];
  const uvs: [number, number][] = [
    [uv[0], uv[1]],
    [uv[2], uv[1]],
    [uv[2], uv[3]],
    [uv[0], uv[3]],
  ];
  for (let i = 0; i < 4; i++) {
    b.positions.push(verts[i][0], verts[i][1], verts[i][2]);
    b.normals.push(normal[0], normal[1], normal[2]);
    b.uvs.push(uvs[i][0], uvs[i][1]);
    b.surfaces.push(surface);
    b.rooms.push(room);
  }
  b.indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
}

function finish(b: Builder): BufferGeometry | null {
  if (b.indices.length === 0) return null;
  const geometry = new BufferGeometry();
  geometry.setAttribute('position', new BufferAttribute(new Float32Array(b.positions), 3));
  geometry.setAttribute('normal', new BufferAttribute(new Float32Array(b.normals), 3));
  geometry.setAttribute('uv', new BufferAttribute(new Float32Array(b.uvs), 2));
  geometry.setAttribute('aSurface', new BufferAttribute(new Float32Array(b.surfaces), 1));
  geometry.setAttribute('aRoom', new BufferAttribute(new Float32Array(b.rooms), 1));
  geometry.setIndex(b.indices);
  geometry.computeBoundingSphere();
  return geometry;
}

export interface ChunkGeometry {
  geometry: BufferGeometry;
  chunkX: number;
  chunkY: number;
}

export function buildChunks(level: Level): ChunkGeometry[] {
  const out: ChunkGeometry[] = [];
  const chunksX = Math.ceil(level.width / CHUNK_TILES);
  const chunksY = Math.ceil(level.height / CHUNK_TILES);
  const halfW = level.width / 2;
  const halfH = level.height / 2;

  const isFloor = (x: number, y: number): boolean =>
    x >= 0 && y >= 0 && x < level.width && y < level.height && level.tiles[y * level.width + x] === FLOOR;

  for (let cy = 0; cy < chunksY; cy++) {
    for (let cx = 0; cx < chunksX; cx++) {
      const b = newBuilder();
      const x0 = cx * CHUNK_TILES;
      const y0 = cy * CHUNK_TILES;
      const x1 = Math.min(level.width, x0 + CHUNK_TILES);
      const y1 = Math.min(level.height, y0 + CHUNK_TILES);

      for (let ty = y0; ty < y1; ty++) {
        for (let tx = x0; tx < x1; tx++) {
          if (!isFloor(tx, ty)) continue;
          const room = level.roomOf[ty * level.width + tx];
          const ceiling = room >= 0 ? level.rooms[room].ceilingHeight : CEILING_HEIGHT;
          const roomAttr = room < 0 ? 0 : room;

          const wx0 = (tx - halfW) * TILE_SIZE;
          const wx1 = wx0 + TILE_SIZE;
          const wz0 = (ty - halfH) * TILE_SIZE;
          const wz1 = wz0 + TILE_SIZE;

          // Floor and ceiling share world-space UVs so the carpet pattern is continuous
          // across tile boundaries and never reads as a grid.
          const u0 = wx0 / TILE_SIZE;
          const u1 = wx1 / TILE_SIZE;
          const v0 = wz0 / TILE_SIZE;
          const v1 = wz1 / TILE_SIZE;

          quad(
            b,
            [wx0, 0, wz1],
            [wx1, 0, wz1],
            [wx1, 0, wz0],
            [wx0, 0, wz0],
            [0, 1, 0],
            [u0, v1, u1, v0],
            SURFACE_FLOOR,
            roomAttr,
          );

          quad(
            b,
            [wx0, ceiling, wz0],
            [wx1, ceiling, wz0],
            [wx1, ceiling, wz1],
            [wx0, ceiling, wz1],
            [0, -1, 0],
            [u0, v0, u1, v1],
            SURFACE_CEILING,
            roomAttr,
          );

          // Walls face inward, into the floor tile that owns them.
          const wallV1 = ceiling / 3;
          if (!isFloor(tx + 1, ty)) {
            quad(
              b,
              [wx1, 0, wz0],
              [wx1, 0, wz1],
              [wx1, ceiling, wz1],
              [wx1, ceiling, wz0],
              [-1, 0, 0],
              [v0, 0, v1, wallV1],
              SURFACE_WALL,
              roomAttr,
            );
          }
          if (!isFloor(tx - 1, ty)) {
            quad(
              b,
              [wx0, 0, wz1],
              [wx0, 0, wz0],
              [wx0, ceiling, wz0],
              [wx0, ceiling, wz1],
              [1, 0, 0],
              [v1, 0, v0, wallV1],
              SURFACE_WALL,
              roomAttr,
            );
          }
          if (!isFloor(tx, ty + 1)) {
            quad(
              b,
              [wx1, 0, wz1],
              [wx0, 0, wz1],
              [wx0, ceiling, wz1],
              [wx1, ceiling, wz1],
              [0, 0, -1],
              [u1, 0, u0, wallV1],
              SURFACE_WALL,
              roomAttr,
            );
          }
          if (!isFloor(tx, ty - 1)) {
            quad(
              b,
              [wx0, 0, wz0],
              [wx1, 0, wz0],
              [wx1, ceiling, wz0],
              [wx0, ceiling, wz0],
              [0, 0, 1],
              [u0, 0, u1, wallV1],
              SURFACE_WALL,
              roomAttr,
            );
          }
        }
      }

      const geometry = finish(b);
      if (geometry) out.push({ geometry, chunkX: cx, chunkY: cy });
    }
  }
  return out;
}

/**
 * Emissive plates for the ceiling fixtures. These are what bloom actually grabs — the
 * light grid provides the illumination, but without a visible source the rooms look lit
 * by nothing.
 */
export function buildFixtureGeometry(level: Level): { geometry: BufferGeometry; rooms: Int16Array } {
  const b = newBuilder();
  const rooms = new Int16Array(level.fixtures.length);
  const halfW = level.width / 2;
  const halfH = level.height / 2;

  level.fixtures.forEach((fixture, i) => {
    rooms[i] = fixture.room;
    const room = fixture.room >= 0 ? level.rooms[fixture.room] : null;
    const ceiling = room ? room.ceilingHeight : CEILING_HEIGHT;
    const cx = (fixture.x - halfW + 0.5) * TILE_SIZE;
    const cz = (fixture.y - halfH + 0.5) * TILE_SIZE;
    const halfLong = TILE_SIZE * 0.36;
    const halfShort = TILE_SIZE * 0.12;
    const y = ceiling - 0.03;
    quad(
      b,
      [cx - halfLong, y, cz - halfShort],
      [cx + halfLong, y, cz - halfShort],
      [cx + halfLong, y, cz + halfShort],
      [cx - halfLong, y, cz + halfShort],
      [0, -1, 0],
      [0, 0, 1, 1],
      SURFACE_CEILING,
      fixture.room < 0 ? 0 : fixture.room,
    );
  });

  const geometry = finish(b) ?? new BufferGeometry();
  return { geometry, rooms };
}
