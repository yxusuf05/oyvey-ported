/**
 * The one movement function.
 *
 * The client runs this to predict the local player every frame; the server runs it to
 * decide what actually happened. There is deliberately no second implementation — a
 * divergence here is the classic source of rubber-banding, and the only defence is that
 * there is nothing to diverge from.
 *
 * `stepPlayer` mutates its state in place. Callers that need history (prediction replay)
 * copy first via {@link clonePlayerState}.
 */

import { TILE_SIZE } from '../levelgen/types';
import type { GridView } from '../levelgen/grid';
import {
  BACKPEDAL_FACTOR,
  Buttons,
  CROUCH_SPEED,
  GROUND_ACCEL,
  GROUND_FRICTION,
  NOISE_CROUCH,
  NOISE_SPRINT,
  NOISE_WALK,
  PLAYER_RADIUS,
  SPRINT_SPEED,
  STAMINA_DRAIN,
  STAMINA_EXHAUSTED,
  STAMINA_MAX,
  STAMINA_REGEN,
  STAMINA_REGEN_DELAY,
  STAMINA_UNLOCK,
  WALK_SPEED,
  hasButton,
} from './constants';

export interface Input {
  seq: number;
  buttons: number;
  yaw: number;
  pitch: number;
  slot: number;
}

export interface PlayerSimState {
  x: number;
  z: number;
  vx: number;
  vz: number;
  yaw: number;
  pitch: number;
  stamina: number;
  crouching: boolean;
  /** Locked out of sprinting until stamina recovers past the unlock threshold. */
  exhausted: boolean;
  sinceSprint: number;
  /** Loudness generated during the last step — consumed by the server's noise field. */
  noise: number;
}

export function createPlayerState(x: number, z: number, yaw = 0): PlayerSimState {
  return {
    x,
    z,
    vx: 0,
    vz: 0,
    yaw,
    pitch: 0,
    stamina: STAMINA_MAX,
    crouching: false,
    exhausted: false,
    sinceSprint: 99,
    noise: 0,
  };
}

export function clonePlayerState(s: PlayerSimState): PlayerSimState {
  return { ...s };
}

export function copyPlayerState(from: PlayerSimState, to: PlayerSimState): void {
  to.x = from.x;
  to.z = from.z;
  to.vx = from.vx;
  to.vz = from.vz;
  to.yaw = from.yaw;
  to.pitch = from.pitch;
  to.stamina = from.stamina;
  to.crouching = from.crouching;
  to.exhausted = from.exhausted;
  to.sinceSprint = from.sinceSprint;
  to.noise = from.noise;
}

export function stepPlayer(
  state: PlayerSimState,
  input: Input,
  grid: GridView,
  dt: number,
  frozen = false,
): void {
  state.yaw = input.yaw;
  state.pitch = input.pitch;

  if (frozen) {
    state.vx = 0;
    state.vz = 0;
    state.noise = 0;
    return;
  }

  let forward = 0;
  let strafe = 0;
  if (hasButton(input.buttons, Buttons.Forward)) forward += 1;
  if (hasButton(input.buttons, Buttons.Back)) forward -= 1;
  if (hasButton(input.buttons, Buttons.Right)) strafe += 1;
  if (hasButton(input.buttons, Buttons.Left)) strafe -= 1;

  const moving = forward !== 0 || strafe !== 0;
  state.crouching = hasButton(input.buttons, Buttons.Crouch);

  // Sprinting requires forward intent, no crouch, and stamina left in the tank.
  const wantsSprint =
    hasButton(input.buttons, Buttons.Sprint) && forward > 0 && !state.crouching && !state.exhausted;

  if (wantsSprint && moving) {
    state.stamina -= STAMINA_DRAIN * dt;
    state.sinceSprint = 0;
    if (state.stamina <= STAMINA_EXHAUSTED) {
      state.stamina = 0;
      state.exhausted = true;
    }
  } else {
    state.sinceSprint += dt;
    if (state.sinceSprint >= STAMINA_REGEN_DELAY) {
      state.stamina = Math.min(STAMINA_MAX, state.stamina + STAMINA_REGEN * dt);
    }
    if (state.exhausted && state.stamina >= STAMINA_UNLOCK) state.exhausted = false;
  }

  const sprinting = wantsSprint && moving && state.stamina > 0;
  let speed = state.crouching ? CROUCH_SPEED : sprinting ? SPRINT_SPEED : WALK_SPEED;
  if (forward < 0) speed *= BACKPEDAL_FACTOR;

  // Direction from yaw. Trig lives on the movement path only, never in level generation:
  // a fractional difference here costs a millimetre of reconciliation, whereas in the
  // generator it would cost two players a shared maze.
  const sin = Math.sin(state.yaw);
  const cos = Math.cos(state.yaw);
  let dirX = 0;
  let dirZ = 0;
  if (moving) {
    // Screen-space intent rotated into world space; -Z is forward.
    const rawX = strafe;
    const rawZ = -forward;
    const inv = 1 / Math.sqrt(rawX * rawX + rawZ * rawZ);
    const nx = rawX * inv;
    const nz = rawZ * inv;
    dirX = nx * cos - nz * sin;
    dirZ = nx * sin + nz * cos;
  }

  const targetVx = dirX * speed;
  const targetVz = dirZ * speed;

  if (moving) {
    state.vx += (targetVx - state.vx) * Math.min(1, GROUND_ACCEL * dt);
    state.vz += (targetVz - state.vz) * Math.min(1, GROUND_ACCEL * dt);
  } else {
    const drop = Math.min(1, GROUND_FRICTION * dt);
    state.vx -= state.vx * drop;
    state.vz -= state.vz * drop;
    if (Math.abs(state.vx) < 1e-4) state.vx = 0;
    if (Math.abs(state.vz) < 1e-4) state.vz = 0;
  }

  state.x += state.vx * dt;
  state.z += state.vz * dt;
  resolveCollision(state, grid);

  const actualSpeed = Math.sqrt(state.vx * state.vx + state.vz * state.vz);
  if (actualSpeed < 0.25) state.noise = 0;
  else if (state.crouching) state.noise = NOISE_CROUCH;
  else if (sprinting) state.noise = NOISE_SPRINT;
  else state.noise = NOISE_WALK * Math.min(1, actualSpeed / WALK_SPEED);
}

/**
 * Circle-versus-tile-grid resolution. Two passes so a player wedged into a corner is
 * pushed out of both walls rather than oscillating between them.
 */
export function resolveCollision(
  state: { x: number; z: number; vx: number; vz: number },
  grid: GridView,
  radius: number = PLAYER_RADIUS,
): void {
  const halfW = grid.width / 2;
  const halfH = grid.height / 2;
  const r = radius;

  for (let iter = 0; iter < 2; iter++) {
    const minTx = Math.floor((state.x - r) / TILE_SIZE + halfW);
    const maxTx = Math.floor((state.x + r) / TILE_SIZE + halfW);
    const minTy = Math.floor((state.z - r) / TILE_SIZE + halfH);
    const maxTy = Math.floor((state.z + r) / TILE_SIZE + halfH);
    let corrected = false;

    for (let ty = minTy; ty <= maxTy; ty++) {
      for (let tx = minTx; tx <= maxTx; tx++) {
        const solid =
          tx < 0 || ty < 0 || tx >= grid.width || ty >= grid.height || grid.tiles[ty * grid.width + tx] === 0;
        if (!solid) continue;

        const minX = (tx - halfW) * TILE_SIZE;
        const maxX = minX + TILE_SIZE;
        const minZ = (ty - halfH) * TILE_SIZE;
        const maxZ = minZ + TILE_SIZE;

        const closestX = state.x < minX ? minX : state.x > maxX ? maxX : state.x;
        const closestZ = state.z < minZ ? minZ : state.z > maxZ ? maxZ : state.z;
        const dx = state.x - closestX;
        const dz = state.z - closestZ;
        const d2 = dx * dx + dz * dz;
        if (d2 >= r * r) continue;

        if (d2 > 1e-8) {
          const d = Math.sqrt(d2);
          const push = (r - d) / d;
          state.x += dx * push;
          state.z += dz * push;
          // Cancel the velocity component pointing into the wall so we slide instead of sticking.
          const nx = dx / d;
          const nz = dz / d;
          const into = state.vx * nx + state.vz * nz;
          if (into < 0) {
            state.vx -= nx * into;
            state.vz -= nz * into;
          }
        } else {
          // Centre is inside the tile: eject along the shallowest axis.
          const toLeft = state.x - minX;
          const toRight = maxX - state.x;
          const toTop = state.z - minZ;
          const toBottom = maxZ - state.z;
          const min = Math.min(toLeft, toRight, toTop, toBottom);
          if (min === toLeft) state.x = minX - r;
          else if (min === toRight) state.x = maxX + r;
          else if (min === toTop) state.z = minZ - r;
          else state.z = maxZ + r;
          state.vx = 0;
          state.vz = 0;
        }
        corrected = true;
      }
    }
    if (!corrected) break;
  }
}

/** Positional error between prediction and authority, used to decide whether to replay. */
export function positionError(a: PlayerSimState, b: PlayerSimState): number {
  const dx = a.x - b.x;
  const dz = a.z - b.z;
  return Math.sqrt(dx * dx + dz * dz);
}
