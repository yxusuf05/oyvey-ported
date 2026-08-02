/**
 * Entity AI: hierarchical state machines with named, player-legible states.
 *
 * Behaviour trees were the obvious alternative and were rejected on purpose. The design
 * requires that each entity be a rule the player can learn, which means the player has to
 * be able to *read* what it is doing — an entity that visibly stiffens, turns, and then
 * commits. Named states can be shown in a debug overlay, logged, and asserted in tests;
 * a tree's control flow hides exactly the thing that needs tuning.
 */

import { AiState, type AiStateId } from '@game/shared/protocol';
import { TILE_SIZE, tileToWorld, worldToTile, type Level } from '@game/shared/levelgen';
import { hasLineOfSight, type GridView } from '@game/shared/levelgen';
import { lightAt } from '@game/shared/levelgen';
import { resolveCollision, type NoiseField } from '@game/shared/sim';
import { clamp, damp } from '@game/shared/math';
import type { EntitySpec } from '@game/shared/content';
import type { Director } from './director';
import type { Navigator, NavTile } from './nav';

export interface ServerEntity {
  id: number;
  kind: string;
  spec: EntitySpec;
  x: number;
  z: number;
  vx: number;
  vz: number;
  yaw: number;
  state: AiStateId;
  stateTime: number;
  /** Rises with perception and decays; the ramp is what players learn to read. */
  awareness: number;
  path: NavTile[];
  pathIndex: number;
  repathCooldown: number;
  target: NavTile | null;
  targetPlayerId: number;
  attackCooldown: number;
  telegraph: number;
  hp: number;
  /** Think slots are staggered so entities never all deliberate on the same tick. */
  thinkPhase: number;
  thinkAccum: number;
  /** Set once per think when the entity emitted a footstep, for client audio. */
  stepAccum: number;
}

export interface AiPlayerView {
  id: number;
  x: number;
  z: number;
  tileX: number;
  tileY: number;
  alive: boolean;
  downed: boolean;
  crouching: boolean;
}

export interface AiWorld {
  level: Level;
  grid: GridView;
  nav: Navigator;
  noise: NoiseField;
  lightField: Uint8Array;
  players: AiPlayerView[];
  descent: number;
  director: Director;
  random: () => number;
  emitSound(key: string, x: number, z: number, loudness: number): void;
  damagePlayer(playerId: number, amount: number, byEntity: number): void;
  onScare(entityId: number, kind: string, intensity: number): void;
}

/** Think at 10 Hz. Movement still integrates every tick, only the decisions are throttled. */
const THINK_INTERVAL = 0.1;

export function createEntity(id: number, kind: string, spec: EntitySpec, tile: NavTile, level: Level): ServerEntity {
  const world = tileToWorld(level, tile.x, tile.y);
  return {
    id,
    kind,
    spec,
    x: world.x,
    z: world.z,
    vx: 0,
    vz: 0,
    yaw: 0,
    state: AiState.Patrol,
    stateTime: 0,
    awareness: 0,
    path: [],
    pathIndex: 0,
    repathCooldown: 0,
    target: null,
    targetPlayerId: -1,
    attackCooldown: 0,
    telegraph: 0,
    hp: 100,
    thinkPhase: (id * 37) % 6,
    thinkAccum: (id % 6) * (THINK_INTERVAL / 6),
    stepAccum: 0,
  };
}

export function updateEntity(entity: ServerEntity, world: AiWorld, dt: number): void {
  entity.stateTime += dt;
  entity.attackCooldown = Math.max(0, entity.attackCooldown - dt);
  entity.repathCooldown = Math.max(0, entity.repathCooldown - dt);

  entity.thinkAccum += dt;
  if (entity.thinkAccum >= THINK_INTERVAL) {
    think(entity, world, entity.thinkAccum);
    entity.thinkAccum = 0;
  }

  move(entity, world, dt);
}

function setState(entity: ServerEntity, next: AiStateId, world: AiWorld): void {
  if (entity.state === next) return;
  const wasHunting = entity.state === AiState.Hunting || entity.state === AiState.Lunge;
  const willHunt = next === AiState.Hunting || next === AiState.Lunge;
  if (wasHunting && !willHunt) world.director.endHunt(entity.id);
  if (!wasHunting && willHunt) world.director.beginHunt(entity.id);
  entity.state = next;
  entity.stateTime = 0;
}

/**
 * Perception and state selection.
 *
 * The Blind One is deliberately the first entity in the game: it perceives through
 * exactly one channel, so the lesson ("stop making noise") is unambiguous. Later entities
 * reuse this same function with different spec numbers — sight range, light bias — rather
 * than a different control structure.
 */
function think(entity: ServerEntity, world: AiWorld, dt: number): void {
  const spec = entity.spec;
  const tile = worldToTile(world.level, entity.x, entity.z);

  // --- Hearing ---
  let heard: { x: number; y: number; value: number } | null = null;
  if (spec.hearingRadius > 0) {
    heard = world.noise.loudestNear(tile.x, tile.y, spec.hearingRadius, spec.hearingThreshold);
  }

  // --- Sight (zero-range entities skip this entirely) ---
  let seenPlayer: AiPlayerView | null = null;
  let seenStrength = 0;
  if (spec.sightRange > 0) {
    for (const player of world.players) {
      if (!player.alive) continue;
      const dx = player.x - entity.x;
      const dz = player.z - entity.z;
      const distTiles = Math.sqrt(dx * dx + dz * dz) / TILE_SIZE;
      if (distTiles > spec.sightRange) continue;
      // Yaw uses the same convention as players: forward is (sin yaw, -cos yaw), so the
      // renderer can orient every actor with one formula.
      const angleTo = Math.atan2(dx, -dz);
      let delta = Math.abs(((angleTo - entity.yaw + Math.PI) % (Math.PI * 2)) - Math.PI);
      if (delta > Math.PI) delta = Math.PI * 2 - delta;
      if (delta > spec.sightHalfAngle) continue;
      if (!hasLineOfSight(world.grid, tile.x + 0.5, tile.y + 0.5, player.tileX + 0.5, player.tileY + 0.5)) continue;

      // Light bias is how one framework produces opposite creatures: +1 sees only in the
      // light, -1 only in the dark. The Level Fun partygoer is this number flipped.
      const brightness = lightAt(world.lightField, world.level.width, player.tileX, player.tileY);
      const visibility =
        spec.lightBias >= 0
          ? brightness * spec.lightBias + (1 - Math.abs(spec.lightBias))
          : (1 - brightness) * -spec.lightBias + (1 - Math.abs(spec.lightBias));
      const strength = visibility * (1 - distTiles / spec.sightRange);
      if (strength > seenStrength) {
        seenStrength = strength;
        seenPlayer = player;
      }
    }
  }

  // --- Awareness accumulator with hysteresis ---
  let stimulus = 0;
  if (seenPlayer) stimulus = Math.max(stimulus, seenStrength * 1.6);
  if (heard) stimulus = Math.max(stimulus, clamp(heard.value / 4, 0.25, 1.8));

  if (stimulus > 0) entity.awareness = Math.min(1.4, entity.awareness + spec.awarenessRise * stimulus * dt);
  else entity.awareness = Math.max(0, entity.awareness - spec.awarenessDecay * dt);

  // --- Target selection ---
  if (seenPlayer) {
    entity.targetPlayerId = seenPlayer.id;
    entity.target = { x: seenPlayer.tileX, y: seenPlayer.tileY };
  } else if (heard) {
    entity.targetPlayerId = -1;
    // Positional error grows with distance, so a blind hunter arrives roughly where the
    // sound was, not exactly on top of you. Being "nearly found" is the scary part.
    const distTiles = Math.abs(heard.x - tile.x) + Math.abs(heard.y - tile.y);
    const spread = Math.min(4, Math.floor(distTiles / 8));
    const jx = heard.x + Math.round((world.random() * 2 - 1) * spread);
    const jy = heard.y + Math.round((world.random() * 2 - 1) * spread);
    entity.target = world.nav.passable(jx, jy) ? { x: jx, y: jy } : { x: heard.x, y: heard.y };
  }

  // --- Attack resolution takes priority over everything else ---
  if (entity.state === AiState.Lunge) {
    entity.telegraph -= dt;
    if (entity.telegraph <= 0) {
      const victim = nearestPlayerWithin(entity, world, spec.attackRange * 1.6);
      if (victim) {
        world.damagePlayer(victim.id, spec.attackDamage, entity.id);
        world.onScare(entity.id, 'attack', 1);
      }
      entity.attackCooldown = spec.attackCooldown;
      setState(entity, AiState.Cooldown, world);
    }
    return;
  }
  if (entity.state === AiState.Cooldown) {
    // Drop back to investigating, not straight to hunting. Re-entering the hunt directly
    // would bypass the director's concurrency cap, and a cooldown cycle is exactly how an
    // entity used to smuggle itself back into a slot it no longer held.
    if (entity.stateTime >= 1.2) setState(entity, AiState.Investigate, world);
    return;
  }

  const victim = nearestPlayerWithin(entity, world, spec.attackRange);
  if (
    victim &&
    entity.attackCooldown <= 0 &&
    entity.awareness >= spec.huntThreshold * 0.6 &&
    world.director.canHunt(entity.id, world.descent)
  ) {
    // Every lethal action is telegraphed. Without this the game is not hard, it is random.
    entity.telegraph = spec.telegraphSeconds;
    entity.targetPlayerId = victim.id;
    setState(entity, AiState.Lunge, world);
    world.emitSound('entity.telegraph', entity.x, entity.z, 12);
    world.onScare(entity.id, 'telegraph', 0.6);
    return;
  }

  // --- State selection ---
  if (entity.awareness >= spec.huntThreshold && world.director.canHunt(entity.id, world.descent)) {
    if (entity.state !== AiState.Hunting) {
      world.emitSound('entity.alerted', entity.x, entity.z, 10);
      world.onScare(entity.id, 'alerted', 0.35);
    }
    setState(entity, AiState.Hunting, world);
  } else if (entity.awareness >= 0.35) {
    setState(entity, AiState.Investigate, world);
  } else if (entity.awareness >= 0.12) {
    setState(entity, AiState.Suspicious, world);
  } else {
    setState(entity, AiState.Patrol, world);
    if (!entity.target || reachedTarget(entity, world)) {
      entity.target = world.nav.randomReachableTile(world.random, tile, 30);
      entity.path = [];
    }
  }

  // --- Repath ---
  const needsPath =
    entity.path.length === 0 ||
    entity.pathIndex >= entity.path.length ||
    (entity.repathCooldown <= 0 && entity.state === AiState.Hunting);
  if (needsPath && entity.target) {
    const path = world.nav.findPath(tile, entity.target);
    if (path && path.length > 0) {
      entity.path = path;
      entity.pathIndex = 0;
    } else {
      entity.path = [];
      entity.target = null;
      entity.awareness *= 0.5;
    }
    entity.repathCooldown = entity.state === AiState.Hunting ? 0.5 : 1.5;
  }
}

function reachedTarget(entity: ServerEntity, world: AiWorld): boolean {
  if (!entity.target) return true;
  const tile = worldToTile(world.level, entity.x, entity.z);
  return Math.abs(tile.x - entity.target.x) + Math.abs(tile.y - entity.target.y) <= 1;
}

function nearestPlayerWithin(entity: ServerEntity, world: AiWorld, range: number): AiPlayerView | null {
  let best: AiPlayerView | null = null;
  let bestDist = range;
  for (const player of world.players) {
    if (!player.alive) continue;
    const dx = player.x - entity.x;
    const dz = player.z - entity.z;
    const d = Math.sqrt(dx * dx + dz * dz);
    if (d < bestDist) {
      bestDist = d;
      best = player;
    }
  }
  return best;
}

/** Steering along the current path, with the same collision resolution players use. */
function move(entity: ServerEntity, world: AiWorld, dt: number): void {
  const spec = entity.spec;
  let speed = 0;
  switch (entity.state) {
    case AiState.Patrol:
      speed = spec.patrolSpeed;
      break;
    case AiState.Suspicious:
      speed = spec.patrolSpeed * 0.5;
      break;
    case AiState.Investigate:
      speed = spec.investigateSpeed;
      break;
    case AiState.Hunting:
      speed = spec.huntSpeed;
      break;
    // Standing still through the telegraph is what makes it a warning rather than a
    // formality — the player gets a beat in which to move.
    case AiState.Lunge:
    case AiState.Cooldown:
    case AiState.Stunned:
    default:
      speed = 0;
  }

  if (speed <= 0 || entity.pathIndex >= entity.path.length) {
    entity.vx = damp(entity.vx, 0, 8, dt);
    entity.vz = damp(entity.vz, 0, 8, dt);
  } else {
    const waypoint = entity.path[entity.pathIndex];
    const target = tileToWorld(world.level, waypoint.x, waypoint.y);
    const dx = target.x - entity.x;
    const dz = target.z - entity.z;
    const d = Math.sqrt(dx * dx + dz * dz);
    if (d < 0.35) {
      entity.pathIndex++;
    } else {
      const nx = dx / d;
      const nz = dz / d;
      entity.vx = damp(entity.vx, nx * speed, 9, dt);
      entity.vz = damp(entity.vz, nz * speed, 9, dt);
      entity.yaw = Math.atan2(nx, -nz);
    }
  }

  entity.x += entity.vx * dt;
  entity.z += entity.vz * dt;
  resolveCollision(entity, world.grid, spec.radius);

  // Monsters make noise too. It is the player's only warning from an entity they cannot
  // see, and it is why the audio subtitle system covers entity sounds as well.
  const speedNow = Math.sqrt(entity.vx * entity.vx + entity.vz * entity.vz);
  if (speedNow > 0.4) {
    entity.stepAccum += speedNow * dt;
    const stride = entity.state === AiState.Hunting ? 1.1 : 1.9;
    if (entity.stepAccum >= stride) {
      entity.stepAccum = 0;
      world.emitSound(entity.state === AiState.Hunting ? 'entity.run' : 'entity.step', entity.x, entity.z, 4);
    }
  }
}
