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
  /**
   * Where this player is looking, and whether their torch is on.
   *
   * All three come from the authoritative `ServerPlayer`, never from a client claim. That
   * distinction is the whole reason the Watcher is safe: "I am not looking at it right now"
   * is precisely the sentence a modified client would want to be believed about.
   */
  yaw: number;
  flashlightOn: boolean;
  focusBeam: boolean;
}

/** A light in the world an entity can steer toward. Not a renderer light — a destination. */
export interface AiLight {
  x: number;
  z: number;
  /** Metres of useful reach; bigger wins ties at equal distance. */
  strength: number;
}

export interface AiWorld {
  level: Level;
  grid: GridView;
  nav: Navigator;
  noise: NoiseField;
  lightField: Uint8Array;
  players: AiPlayerView[];
  /** Everything currently giving off light: cracked glowsticks and lit torches. */
  lights: AiLight[];
  descent: number;
  director: Director;
  random: () => number;
  emitSound(key: string, x: number, z: number, loudness: number): void;
  damagePlayer(playerId: number, amount: number, byEntity: number): void;
  onScare(entityId: number, kind: string, intensity: number): void;
}

/** Think at 10 Hz. Movement still integrates every tick, only the decisions are throttled. */
const THINK_INTERVAL = 0.1;

/**
 * The flashlight cone, as the simulation sees it.
 *
 * These mirror the renderer's cone in `session.ts:updateLights` — 30 degrees to the edge
 * wide, 15 focused — because a player who can see something lit has to be able to trust
 * that the game agrees. Kept as plain numbers rather than shared constants so a purely
 * visual tweak to the torch cannot silently retune a monster.
 */
const BEAM_HALF_ANGLE_WIDE = 0.54;
const BEAM_HALF_ANGLE_FOCUS = 0.27;
const BEAM_RANGE_WIDE = 19;
const BEAM_RANGE_FOCUS = 30;

/** How wide a glance counts as "looking at it" for the Watcher. Roughly a 90° view. */
const GAZE_HALF_ANGLE = 0.78;

export interface ObserveOptions {
  /** Half-angle of the cone, radians. */
  halfAngle: number;
  /** Metres. */
  range: number;
  /** Only count players whose torch is on, and use the beam's own cone and reach. */
  requireFlashlight?: boolean;
}

/**
 * The first living player who has this entity inside the given cone, with line of sight.
 *
 * One function, two very different monsters: the Smiler asks with `requireFlashlight` and
 * freezes; the Watcher asks with a wide cone and stops moving. Sharing the geometry means
 * there is exactly one place where "can that player see it" can be wrong.
 */
export function observedBy(entity: ServerEntity, world: AiWorld, options: ObserveOptions): AiPlayerView | null {
  const tile = worldToTile(world.level, entity.x, entity.z);

  for (const player of world.players) {
    // A body on the floor is not watching anything, and neither is a corpse.
    if (!player.alive || player.downed) continue;
    if (options.requireFlashlight && !player.flashlightOn) continue;

    const halfAngle = options.requireFlashlight
      ? player.focusBeam
        ? BEAM_HALF_ANGLE_FOCUS
        : BEAM_HALF_ANGLE_WIDE
      : options.halfAngle;
    const range = options.requireFlashlight
      ? player.focusBeam
        ? BEAM_RANGE_FOCUS
        : BEAM_RANGE_WIDE
      : options.range;

    const dx = entity.x - player.x;
    const dz = entity.z - player.z;
    const distance = Math.sqrt(dx * dx + dz * dz);
    if (distance > range || distance < 0.001) continue;

    // Same yaw convention as everything else: forward is (sin yaw, -cos yaw).
    const angleTo = Math.atan2(dx, -dz);
    let delta = Math.abs(((angleTo - player.yaw + Math.PI) % (Math.PI * 2)) - Math.PI);
    if (delta > Math.PI) delta = Math.PI * 2 - delta;
    if (delta > halfAngle) continue;

    if (!hasLineOfSight(world.grid, player.tileX + 0.5, player.tileY + 0.5, tile.x + 0.5, tile.y + 0.5)) continue;
    return player;
  }
  return null;
}

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

  // The Smiler, pinned by a torch. Checked before anything else, including the lunge
  // resolution, so a beam brought up during the telegraph actually saves the player —
  // a freeze that only applied between attacks would not be a counter, it would be a
  // decoration.
  if (spec.freezesInBeam) {
    if (observedBy(entity, world, { halfAngle: 0, range: 0, requireFlashlight: true })) {
      if (entity.state !== AiState.Stunned) {
        world.emitSound('entity.smilerFreeze', entity.x, entity.z, 6);
        world.onScare(entity.id, 'smilerFreeze', 0.4);
      }
      setState(entity, AiState.Stunned, world);
      // Awareness keeps decaying while it is held, so holding the light on it long enough
      // actually calms it down rather than merely pausing the problem.
      entity.awareness = Math.max(0, entity.awareness - spec.awarenessDecay * dt);
      return;
    }
    if (entity.state === AiState.Stunned) setState(entity, AiState.Investigate, world);
  }

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

  // --- Swarms steer toward light, and stop there ---
  //
  // Deliberately resolved before the awareness machinery: a swarm has no interest in you,
  // so running it through "notice the player, escalate, hunt" would give it a chase it is
  // not supposed to have — and, worse, would spend a director hunter slot per body.
  if (spec.swarm) {
    swarmThink(entity, world, dt, tile);
    return;
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

/**
 * Swarm behaviour: walk to the brightest thing you can reach, bite whatever is standing
 * next to it.
 *
 * There is no `Hunting` state here and `canHunt` is never called, so a swarm cannot consume
 * the director's budget. The player's counter falls out of that: drop a glowstick, walk
 * away from it, and the swarm goes to the light rather than to you.
 */
function swarmThink(entity: ServerEntity, world: AiWorld, dt: number, tile: NavTile): void {
  const spec = entity.spec;

  if (entity.state === AiState.Lunge) {
    entity.telegraph -= dt;
    if (entity.telegraph <= 0) {
      const victim = nearestPlayerWithin(entity, world, spec.attackRange * 1.5);
      // Small damage, short warning. The threat is the arithmetic of five of them, not any
      // one bite — but it still telegraphs, because "nothing lethal without warning" has to
      // hold for the thing that finishes you as much as for the thing that starts it.
      if (victim) world.damagePlayer(victim.id, spec.attackDamage, entity.id);
      entity.attackCooldown = spec.attackCooldown;
      setState(entity, AiState.Cooldown, world);
    }
    return;
  }
  if (entity.state === AiState.Cooldown) {
    if (entity.stateTime >= 0.4) setState(entity, AiState.Investigate, world);
    return;
  }

  const victim = nearestPlayerWithin(entity, world, spec.attackRange);
  if (victim && entity.attackCooldown <= 0) {
    entity.telegraph = spec.telegraphSeconds;
    entity.targetPlayerId = victim.id;
    setState(entity, AiState.Lunge, world);
    return;
  }

  const light = brightestLight(entity, world);
  if (light) {
    entity.targetPlayerId = -1;
    const lightTile = worldToTile(world.level, light.x, light.z);
    if (world.nav.passable(lightTile.x, lightTile.y)) entity.target = lightTile;
    setState(entity, AiState.Investigate, world);
  } else {
    setState(entity, AiState.Patrol, world);
    if (!entity.target || reachedTarget(entity, world)) {
      entity.target = world.nav.randomReachableTile(world.random, tile, 18);
      entity.path = [];
    }
  }

  const needsPath = entity.path.length === 0 || entity.pathIndex >= entity.path.length || entity.repathCooldown <= 0;
  if (needsPath && entity.target) {
    const path = world.nav.findPath(tile, entity.target);
    if (path && path.length > 0) {
      entity.path = path;
      entity.pathIndex = 0;
    } else {
      // Keep the light as the goal and try again next second rather than forgetting it.
      // A swarm that gave up the moment one path lookup failed would drift off the moment
      // it crossed an awkward threshold, and the bait would stop working for no visible
      // reason.
      entity.path = [];
    }
    entity.repathCooldown = 1;
  }
}

/**
 * The most attractive light within reach: nearer and stronger wins.
 *
 * Range falls off with distance rather than being a hard radius, so a lone glowstick at the
 * far end of a hall still pulls — that is the whole point of using one as bait.
 */
function brightestLight(entity: ServerEntity, world: AiWorld): AiLight | null {
  let best: AiLight | null = null;
  let bestScore = 0;
  for (const light of world.lights) {
    const distance = Math.hypot(light.x - entity.x, light.z - entity.z);
    const score = light.strength / (1 + distance * 0.5);
    if (score > bestScore) {
      bestScore = score;
      best = light;
    }
  }
  return best;
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

  // The Watcher stops dead while anyone has eyes on it — but keeps its state machine
  // running underneath, so the debug overlay and the tests still show what it *wants* to
  // do. A creature that reported "Patrol" while frozen would be lying about itself.
  if (spec.movesOnlyUnobserved && observedBy(entity, world, { halfAngle: GAZE_HALF_ANGLE, range: spec.sightRange })) {
    entity.vx = 0;
    entity.vz = 0;
    return;
  }

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
