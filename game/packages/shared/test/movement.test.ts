import { describe, expect, it } from 'vitest';
import { generateLevel } from '../src/levelgen';
import { levelGrid, type GridView } from '../src/levelgen/grid';
import { FLOOR, SOLID, TILE_SIZE, tileToWorld } from '../src/levelgen/types';
import { Rng } from '../src/prng';
import {
  Buttons,
  DT,
  PLAYER_RADIUS,
  SPRINT_SPEED,
  STAMINA_MAX,
  WALK_SPEED,
  createPlayerState,
  stepPlayer,
  type Input,
} from '../src/sim';

const input = (buttons: number, yaw = 0): Input => ({ seq: 1, buttons, yaw, pitch: 0, slot: 0 });

function openRoom(size = 16): GridView {
  const grid: GridView = { width: size, height: size, tiles: new Uint8Array(size * size).fill(FLOOR) };
  for (let i = 0; i < size; i++) {
    grid.tiles[i] = SOLID;
    grid.tiles[(size - 1) * size + i] = SOLID;
    grid.tiles[i * size] = SOLID;
    grid.tiles[i * size + size - 1] = SOLID;
  }
  return grid;
}

describe('stepPlayer', () => {
  it('walks forward along -Z at yaw zero', () => {
    const grid = openRoom();
    const state = createPlayerState(0, 0);
    for (let i = 0; i < 120; i++) stepPlayer(state, input(Buttons.Forward), grid, DT);
    expect(state.z).toBeLessThan(-1);
    expect(Math.abs(state.x)).toBeLessThan(0.001);
  });

  it('sprints faster than it walks, and crouching is slowest', () => {
    const grid = openRoom(40);
    const measure = (buttons: number): number => {
      const state = createPlayerState(0, 0);
      for (let i = 0; i < 90; i++) stepPlayer(state, input(buttons), grid, DT);
      return Math.hypot(state.vx, state.vz);
    };
    const walk = measure(Buttons.Forward);
    const sprint = measure(Buttons.Forward | Buttons.Sprint);
    const crouch = measure(Buttons.Forward | Buttons.Crouch);
    expect(sprint).toBeGreaterThan(walk + 1);
    expect(crouch).toBeLessThan(walk - 1);
    expect(walk).toBeCloseTo(WALK_SPEED, 0);
    expect(sprint).toBeCloseTo(SPRINT_SPEED, 0);
  });

  it('cannot sustain a sprint: holding the key forever averages out near a walk', () => {
    const grid = openRoom(60);
    const state = createPlayerState(0, 0);

    let hitZero = false;
    let everExhausted = false;
    let sprintFrames = 0;
    const total = 60 * 40;
    for (let i = 0; i < total; i++) {
      stepPlayer(state, input(Buttons.Forward | Buttons.Sprint), grid, DT);
      if (state.stamina === 0) hitZero = true;
      if (state.exhausted) everExhausted = true;
      if (Math.hypot(state.vx, state.vz) > WALK_SPEED + 0.5) sprintFrames++;
    }

    expect(hitZero).toBe(true);
    expect(everExhausted).toBe(true);
    // Once exhausted, sprinting only returns above STAMINA_UNLOCK, so the player ends up
    // oscillating rather than either sprinting forever or being permanently crippled.
    const sprintFraction = sprintFrames / total;
    expect(sprintFraction).toBeGreaterThan(0.1);
    expect(sprintFraction).toBeLessThan(0.6);
  });

  it('forces a walk immediately after hitting exhaustion', () => {
    const grid = openRoom(60);
    const state = createPlayerState(0, 0);
    while (!state.exhausted) stepPlayer(state, input(Buttons.Forward | Buttons.Sprint), grid, DT);
    // Give the velocity a moment to decay from sprint to walk speed.
    for (let i = 0; i < 30; i++) stepPlayer(state, input(Buttons.Forward | Buttons.Sprint), grid, DT);
    expect(state.stamina).toBeLessThan(STAMINA_MAX * 0.25);
    expect(Math.hypot(state.vx, state.vz)).toBeLessThan(WALK_SPEED + 0.3);
  });

  it('recovers stamina after a delay and unlocks sprinting again', () => {
    const grid = openRoom(60);
    const state = createPlayerState(0, 0);
    for (let i = 0; i < 60 * 12; i++) stepPlayer(state, input(Buttons.Forward | Buttons.Sprint), grid, DT);
    for (let i = 0; i < 60 * 10; i++) stepPlayer(state, input(0), grid, DT);
    expect(state.stamina).toBeGreaterThan(60);
    expect(state.exhausted).toBe(false);
    expect(state.stamina).toBeLessThanOrEqual(STAMINA_MAX);
  });

  it('reports the loudness the entity AI actually hears', () => {
    const grid = openRoom(40);
    const state = createPlayerState(0, 0);
    const run = (buttons: number): number => {
      for (let i = 0; i < 60; i++) stepPlayer(state, input(buttons), grid, DT);
      return state.noise;
    };
    const sprintNoise = run(Buttons.Forward | Buttons.Sprint);
    const crouchNoise = run(Buttons.Forward | Buttons.Crouch);
    for (let i = 0; i < 60; i++) stepPlayer(state, input(0), grid, DT);
    const idleNoise = state.noise;

    expect(sprintNoise).toBeGreaterThan(crouchNoise);
    expect(idleNoise).toBe(0);
    // Standing still must be genuinely silent, or crouching is not a real counter-play.
    expect(crouchNoise).toBeGreaterThan(0);
  });
});

describe('collision', () => {
  it('never tunnels through a wall, whatever direction it is pushed', () => {
    const grid = openRoom(10);
    const rng = new Rng('collide');
    for (let trial = 0; trial < 300; trial++) {
      const state = createPlayerState(0, 0);
      const yaw = rng.range(-Math.PI, Math.PI);
      for (let i = 0; i < 400; i++) {
        stepPlayer(state, input(Buttons.Forward | Buttons.Sprint, yaw), grid, DT);
      }
      // The room interior spans tiles 1..8, i.e. ±8 m from the centre.
      const limit = (10 / 2 - 1) * TILE_SIZE - PLAYER_RADIUS + 0.001;
      expect(Math.abs(state.x), `x escaped at yaw ${yaw}`).toBeLessThanOrEqual(limit);
      expect(Math.abs(state.z), `z escaped at yaw ${yaw}`).toBeLessThanOrEqual(limit);
    }
  });

  it('slides along a wall instead of sticking to it', () => {
    const grid = openRoom(12);
    const state = createPlayerState(0, 0);
    // Walk into the +X wall at a shallow angle.
    const yaw = Math.PI / 2 - 0.35;
    for (let i = 0; i < 200; i++) stepPlayer(state, input(Buttons.Forward, yaw), grid, DT);
    const speedAlongWall = Math.abs(state.vz);
    expect(speedAlongWall).toBeGreaterThan(0.5);
  });

  it('keeps players inside a real generated level for a long random walk', () => {
    const level = generateLevel('collision', 'level0');
    const grid = levelGrid(level);
    const spawn = tileToWorld(level, level.spawn.x, level.spawn.y);
    const rng = new Rng('walk');

    const state = createPlayerState(spawn.x, spawn.z);
    let yaw = 0;
    for (let i = 0; i < 60 * 90; i++) {
      if (i % 40 === 0) yaw = rng.range(-Math.PI, Math.PI);
      stepPlayer(state, input(Buttons.Forward | Buttons.Sprint, yaw), grid, DT);

      const tx = Math.floor(state.x / TILE_SIZE + level.width / 2);
      const ty = Math.floor(state.z / TILE_SIZE + level.height / 2);
      expect(level.tiles[ty * level.width + tx], `left the floor at step ${i}`).toBe(FLOOR);
    }
  });
});
