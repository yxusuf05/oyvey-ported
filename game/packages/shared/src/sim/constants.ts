/** Tuning constants for the shared simulation. Both sides read these — never fork them. */

export const TICK_RATE = 60;
export const DT = 1 / TICK_RATE;
/** Snapshots go out every third simulation step. */
export const SNAPSHOT_EVERY = 3;
export const SNAPSHOT_RATE = TICK_RATE / SNAPSHOT_EVERY;

export const PLAYER_RADIUS = 0.34;
export const EYE_HEIGHT = 1.62;
export const CROUCH_EYE_HEIGHT = 1.02;

export const WALK_SPEED = 3.1;
export const SPRINT_SPEED = 5.35;
export const CROUCH_SPEED = 1.35;
export const BACKPEDAL_FACTOR = 0.72;

export const GROUND_ACCEL = 42;
export const GROUND_FRICTION = 14;

export const STAMINA_MAX = 100;
export const STAMINA_DRAIN = 19;
export const STAMINA_REGEN = 13;
/** Seconds after sprinting before stamina starts coming back. */
export const STAMINA_REGEN_DELAY = 1.1;
/** Sprinting is locked out below this until stamina recovers past `STAMINA_UNLOCK`. */
export const STAMINA_EXHAUSTED = 1;
export const STAMINA_UNLOCK = 22;

/**
 * Loudness emitted per second of movement. This is gameplay, not audio: the numbers here
 * are what a Sound Hunter actually hears, and the client's footstep synthesis is only a
 * rendering of the same events.
 */
export const NOISE_SPRINT = 6;
export const NOISE_WALK = 3;
export const NOISE_CROUCH = 0.55;
export const NOISE_IDLE = 0;
export const NOISE_ITEM_DROP = 8;
export const NOISE_DECOY = 20;
/** Loudness cost of crossing a wall versus a doorway, in the propagation BFS. */
export const NOISE_WALL_COST = 6;
export const NOISE_DOOR_COST = 1;
/** Fraction of the noise field remaining after one second. */
export const NOISE_DECAY_PER_SEC = 0.45;

export const FLASHLIGHT_BATTERY_MAX = 100;
/** Battery drain per second in wide and focused beam modes. */
export const FLASHLIGHT_DRAIN_WIDE = 0.62;
export const FLASHLIGHT_DRAIN_FOCUS = 1.05;

export const SANITY_MAX = 100;
/** Sanity per second lost in complete darkness and regained in full light. */
export const SANITY_DARK_DRAIN = 2.4;
export const SANITY_LIGHT_REGEN = 3.1;
/** Additional drain per second while an entity has line of sight to you. */
export const SANITY_SEEN_DRAIN = 7.5;
/** Being near a living teammate steadies you. */
export const SANITY_BUDDY_REGEN = 1.6;
export const SANITY_BUDDY_RANGE = 6;

export const PLAYER_MAX_HP = 100;
export const DOWNED_BLEEDOUT_SECONDS = 60;
export const REVIVE_SECONDS = 6;

export const INTERACT_RANGE = 2.6;
export const MAX_PLAYERS = 4;

export const Buttons = {
  Forward: 1 << 0,
  Back: 1 << 1,
  Left: 1 << 2,
  Right: 1 << 3,
  Sprint: 1 << 4,
  Crouch: 1 << 5,
  Interact: 1 << 6,
  UseItem: 1 << 7,
  Flashlight: 1 << 8,
  BeamMode: 1 << 9,
} as const;

export type ButtonMask = number;

export const hasButton = (mask: number, button: number): boolean => (mask & button) !== 0;
