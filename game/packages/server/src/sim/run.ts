/**
 * The authoritative run.
 *
 * Everything that decides who wins lives here: positions, objectives, entity AI, the
 * descent clock, sanity, damage. The client predicts its own movement and renders the
 * rest, but it is never consulted. The one deliberate exception is hallucinations, which
 * the client generates locally from its own sanity value — they cannot affect state, so
 * letting each player hallucinate differently costs nothing and is the best co-op horror
 * beat the game has.
 */

import {
  AiState,
  EntityFlags,
  EntityKind,
  type InventorySlotState,
  type ObjectiveState,
  type RunOutcome,
  type RunStats,
  type ChalkMarkState,
  type S2C,
  type WorldItemState,
} from '@game/shared/protocol';
import {
  FLOOR,
  generateLevel,
  getTheme,
  levelGrid,
  lightAt,
  propagateLight,
  tileToWorld,
  worldToTile,
  type GridView,
  type Level,
  type ThemeSpec,
} from '@game/shared/levelgen';
import {
  Buttons,
  DOWNED_BLEEDOUT_SECONDS,
  FLASHLIGHT_BATTERY_MAX,
  FLASHLIGHT_DRAIN_FOCUS,
  FLASHLIGHT_DRAIN_WIDE,
  INTERACT_RANGE,
  NOISE_ITEM_DROP,
  NoiseField,
  PLAYER_MAX_HP,
  REVIVE_SECONDS,
  SANITY_BUDDY_RANGE,
  SANITY_BUDDY_REGEN,
  SANITY_DARK_DRAIN,
  SANITY_LIGHT_REGEN,
  SANITY_MAX,
  SANITY_SEEN_DRAIN,
  clonePlayerState,
  createPlayerState,
  hasButton,
  stepPlayer,
  type Input,
  type PlayerSimState,
} from '@game/shared/sim';
import { Rng } from '@game/shared/prng';
import { clamp, clamp01 } from '@game/shared/math';
import {
  BACKPACK_SLOTS,
  DEFAULT_LOADOUT,
  EMF_PING_FAST,
  EMF_PING_NOISE,
  EMF_PING_SLOW,
  LOOT_TABLE,
  batteryFactor,
  bonusSlots,
  bonusSupplies,
  consumableFactor,
  getEntitySpec,
  getItemSpec,
  noiseFactor,
  reviveFactor,
  type ItemSpec,
  type PerkLevels,
} from '@game/shared/content';
import { Director, MIN_SPAWN_DISTANCE } from '../ai/director';
import { Navigator } from '../ai/nav';
import {
  createEntity,
  updateEntity,
  type AiLight,
  type AiPlayerView,
  type AiWorld,
  type ServerEntity,
} from '../ai/brains';

/**
 * A dropped item. `burnLeft` is server-only — the client is told what is on the floor, not
 * how much life it has left, because a countdown on a glowstick would turn a piece of
 * atmosphere into a spreadsheet.
 */
export interface WorldItem extends WorldItemState {
  burnLeft: number;
  /** Decoys only: seconds until the next shout. */
  pulseIn: number;
}

/**
 * A fresh backpack: the default loadout laid into fixed slots, the rest left empty.
 *
 * The size comes from the player's perks rather than from the constant, which is the point
 * of the backpack perk — but the extra room always lands at the end, so slot 1 is the
 * flashlight for everyone regardless of what they have bought.
 */
function startingInventory(size: number, perks: PerkLevels): (InventorySlotState | null)[] {
  const slots: (InventorySlotState | null)[] = new Array(size).fill(null);
  const extra = bonusSupplies(perks);
  for (let i = 0; i < DEFAULT_LOADOUT.length && i < size; i++) {
    const entry = DEFAULT_LOADOUT[i];
    const spec = getItemSpec(entry.item);
    // The packer perk tops up what stacks, capped by the stack itself — a perk cannot make
    // a slot hold more than a slot holds.
    const count = spec && spec.stack > 1 ? Math.min(spec.stack, entry.count + extra) : entry.count;
    slots[i] = { item: entry.item, count };
  }
  return slots;
}

export interface ServerPlayer {
  id: number;
  name: string;
  state: PlayerSimState;
  /** Fixed-length backpack. `null` is an empty slot; slots keep their index. */
  inventory: (InventorySlotState | null)[];
  activeSlot: number;
  /** Seconds until the use key does anything again. */
  useCooldown: number;
  /** Set whenever the backpack changes; the room turns it into one message. */
  inventoryDirty: boolean;
  /** Meta-progression this player brought into the run. Read-only during it. */
  perks: PerkLevels;
  /** EMF detector: switched on, and seconds until the next ping. */
  emfOn: boolean;
  emfPingIn: number;
  /** Inputs received but not yet simulated, ordered by sequence. */
  pending: Input[];
  lastProcessedSeq: number;
  lastAckedTick: number;
  lastButtons: number;
  hp: number;
  sanity: number;
  battery: number;
  flashlightOn: boolean;
  focusBeam: boolean;
  downed: boolean;
  bleedout: number;
  reviveProgress: number;
  carrying: number;
  escaped: boolean;
  connected: boolean;
  /** Seconds since the socket dropped; the body stays in the world during the grace period. */
  disconnectedFor: number;
}

export class Run {
  readonly level: Level;
  readonly grid: GridView;
  readonly theme: ThemeSpec;
  readonly nav: Navigator;
  readonly noise: NoiseField;
  readonly objectives: ObjectiveState[];
  readonly director: Director;

  lightField: Uint8Array;
  darkRooms = new Set<number>();
  entities: ServerEntity[] = [];

  descent = 0;
  peakDescent = 0;
  tick = 0;
  elapsed = 0;
  outcome: RunOutcome | null = null;

  /** Drained by the room each tick and forwarded to clients. */
  outbox: S2C[] = [];
  objectivesDirty = false;

  worldItems: WorldItem[] = [];
  worldItemsDirty = false;

  marks: ChalkMarkState[] = [];
  marksDirty = false;

  /** Doors jammed shut against entities. Players walk straight through them. */
  readonly wedgedDoors = new Set<number>();

  private nextMarkId = 1;
  private nextWorldItemId = 1;
  private nextEntityId = 1000;
  private spawnCooldown = 6;
  private descentIndex = 0;
  private readonly rng: Rng;

  constructor(seed: string, themeId: string) {
    this.level = generateLevel(seed, themeId);
    this.theme = getTheme(themeId);
    this.grid = levelGrid(this.level);
    this.nav = new Navigator(this.level);
    this.noise = new NoiseField(this.grid);
    this.lightField = propagateLight(this.grid, this.level.fixtures);
    this.rng = new Rng(`${seed}:run`);
    this.director = new Director(() => this.rng.next());
    this.objectives = this.level.objectives.map((o) => ({
      id: o.id,
      kind: o.kind,
      done: false,
      carriedBy: -1,
    }));
    this.scatterLoot();
  }

  /**
   * Rolls the loot lying around the level.
   *
   * This runs on the server and is *sent*, never regenerated client-side, even though it
   * would be trivially deterministic. Anything that decides who wins belongs to the server;
   * the moment two clients could disagree about how many medkits exist, the rule stops
   * holding for the things that matter too.
   *
   * The stream is derived by name, so adding a later roll cannot shift the maze or the
   * entity spawns that were generated before it.
   */
  private scatterLoot(): void {
    const rng = this.rng.derive('run:itemSpawns');
    const spawnRoom = this.level.roomOf[this.level.spawn.y * this.level.width + this.level.spawn.x];
    const weights = LOOT_TABLE.map((entry) => entry.weight);
    const count = Math.max(4, Math.round(this.level.rooms.length * 0.55));

    for (let i = 0; i < count; i++) {
      const room = this.level.rooms[rng.int(0, this.level.rooms.length - 1)];
      // Nothing in the room you start in: free loot at the door removes the walk that the
      // whole level is made of.
      if (room.id === spawnRoom) continue;

      const tile = this.randomFloorTile(room, rng);
      if (!tile) continue;
      const world = tileToWorld(this.level, tile.x, tile.y);
      const item = rng.weighted(LOOT_TABLE, weights).item;
      const spec = getItemSpec(item);
      if (!spec) continue;

      this.worldItems.push({
        id: this.nextWorldItemId++,
        item,
        x: world.x,
        z: world.z,
        count: spec.stack > 1 ? rng.int(1, Math.min(2, spec.stack)) : 1,
        lit: false,
        burnLeft: 0,
        pulseIn: 0,
      });
    }
    this.worldItemsDirty = true;
  }

  private randomFloorTile(room: { x0: number; y0: number; x1: number; y1: number }, rng: Rng):
    | { x: number; y: number }
    | null {
    for (let attempt = 0; attempt < 12; attempt++) {
      const x = rng.int(room.x0, room.x1);
      const y = rng.int(room.y0, room.y1);
      if (this.grid.tiles[y * this.grid.width + x] === FLOOR) return { x, y };
    }
    return null;
  }

  get fuseTotal(): number {
    return this.objectives.filter((o) => o.kind === 'fuse').length;
  }

  get fusesCollected(): number {
    return this.objectives.filter((o) => o.kind === 'fuse' && o.done).length;
  }

  spawnPoint(): { x: number; z: number } {
    return tileToWorld(this.level, this.level.spawn.x, this.level.spawn.y);
  }

  createPlayer(id: number, name: string, perks: PerkLevels = {}): ServerPlayer {
    const spawn = this.spawnPoint();
    // Fan players out slightly so four bodies do not start inside each other.
    const angle = (id % 8) * (Math.PI / 4);
    return {
      id,
      name,
      state: createPlayerState(spawn.x + Math.cos(angle) * 0.7, spawn.z + Math.sin(angle) * 0.7),
      inventory: startingInventory(BACKPACK_SLOTS + bonusSlots(perks), perks),
      perks,
      activeSlot: 0,
      useCooldown: 0,
      inventoryDirty: true,
      emfOn: false,
      emfPingIn: 0,
      pending: [],
      lastProcessedSeq: 0,
      lastAckedTick: 0,
      lastButtons: 0,
      hp: PLAYER_MAX_HP,
      sanity: SANITY_MAX,
      battery: FLASHLIGHT_BATTERY_MAX,
      flashlightOn: false,
      focusBeam: false,
      downed: false,
      bleedout: DOWNED_BLEEDOUT_SECONDS,
      reviveProgress: 0,
      carrying: -1,
      escaped: false,
      connected: true,
      disconnectedFor: 0,
    };
  }

  step(players: ServerPlayer[], dt: number): void {
    if (this.outcome) return;
    this.tick++;
    this.elapsed += dt;

    this.stepPlayers(players, dt);
    this.stepItems(dt);
    this.stepDetectors(players, dt);
    this.stepDescent(players, dt);
    this.stepEntities(players, dt);
    this.stepSurvival(players, dt);
    this.checkOutcome(players);
  }

  // ---------------------------------------------------------------------------
  // Players
  // ---------------------------------------------------------------------------

  private stepPlayers(players: ServerPlayer[], dt: number): void {
    this.noise.decay(dt);

    for (const player of players) {
      const active = player.connected && !player.escaped && this.isAlive(player);
      // A disconnected body stays put and is ignored by monsters; the player has 90
      // seconds to come back to it rather than losing the run to a dropped connection.
      const frozen = !active || player.downed;

      const input = player.pending.shift();
      if (input) {
        stepPlayer(player.state, input, this.grid, dt, frozen);
        player.lastProcessedSeq = input.seq;
        this.handleButtons(player, input, players);
        player.lastButtons = input.buttons;
      } else if (frozen) {
        stepPlayer(
          player.state,
          { seq: player.lastProcessedSeq, buttons: 0, yaw: player.state.yaw, pitch: player.state.pitch, slot: 0 },
          this.grid,
          dt,
          true,
        );
      }
      // With no input and an active player, the body simply holds position. Synthesising a
      // filler step here would be the intuitive thing to do and is quietly fatal: the
      // authoritative position would stop being a pure function of the input sequence, so
      // the client's replay could never reproduce it and every stalled packet would leave
      // permanent drift behind.

      if (active && !player.downed && player.state.noise > 0) {
        const tile = worldToTile(this.level, player.state.x, player.state.z);
        // Boots quieten your own footsteps and nothing else — not dropped items, not tools,
        // not the decoy. Buying silence should reward how you move, not switch the noise
        // system off.
        this.noise.emit(tile.x, tile.y, player.state.noise * noiseFactor(player.perks));
      }
      // A downed player screams continuously — reviving is meant to be a real risk.
      if (player.downed && this.isAlive(player)) {
        const tile = worldToTile(this.level, player.state.x, player.state.z);
        this.noise.emit(tile.x, tile.y, 4.5);
      }
    }
  }

  private handleButtons(player: ServerPlayer, input: Input, players: ServerPlayer[]): void {
    const pressed = (bit: number): boolean =>
      hasButton(input.buttons, bit) && !hasButton(player.lastButtons, bit);

    // The selected slot arrives with every input and is clamped here rather than trusted.
    // A client that sends slot 200 selects the last slot, not memory past the end of the
    // backpack.
    const slot = clamp(Math.floor(input.slot), 0, player.inventory.length - 1);
    if (slot !== player.activeSlot) {
      player.activeSlot = slot;
      player.inventoryDirty = true;
    }

    if (pressed(Buttons.Flashlight) && player.battery > 0) {
      player.flashlightOn = !player.flashlightOn;
      this.emitSound('item.flashlightClick', player.state.x, player.state.z, 2);
    }
    if (pressed(Buttons.BeamMode) && player.flashlightOn) {
      player.focusBeam = !player.focusBeam;
    }
    if (pressed(Buttons.UseItem)) this.useActiveItem(player);
    if (pressed(Buttons.Drop)) this.dropActiveItem(player);
    if (pressed(Buttons.Interact)) this.handleInteract(player, players);
  }

  // ---------------------------------------------------------------------------
  // Backpack
  // ---------------------------------------------------------------------------

  /** The item in the player's hand, or null if that slot is empty. */
  activeItem(player: ServerPlayer): ItemSpec | null {
    const slot = player.inventory[player.activeSlot];
    return slot ? getItemSpec(slot.item) : null;
  }

  useActiveItem(player: ServerPlayer): void {
    if (player.downed || !this.isAlive(player) || player.escaped) return;
    if (player.useCooldown > 0) return;

    const slot = player.inventory[player.activeSlot];
    if (!slot) return;
    const spec = getItemSpec(slot.item);
    if (!spec || !spec.usable) return;

    switch (spec.kind) {
      case 'tool':
        // The only tool so far is the flashlight, which also has its own key. Routing it
        // through the hotbar as well means the backpack is never a special case with one
        // item mysteriously exempt from it.
        if (spec.id === 'flashlight') {
          if (player.battery <= 0 && !player.flashlightOn) return;
          player.flashlightOn = !player.flashlightOn;
        }
        break;

      case 'placeable': {
        this.spawnWorldItem(spec.id, player.state.x, player.state.z, 1, spec.burnSeconds ?? 0);
        this.consumeActive(player, 1);
        break;
      }

      case 'consumable': {
        const potency = consumableFactor(player.perks);
        if (spec.restoreSanity !== undefined) {
          player.sanity = clamp(player.sanity + spec.restoreSanity * potency, 0, SANITY_MAX);
        }
        if (spec.restoreHp !== undefined) {
          player.hp = clamp(player.hp + spec.restoreHp * potency, 0, PLAYER_MAX_HP);
        }
        this.consumeActive(player, 1);
        break;
      }

      case 'marker': {
        if (!this.drawMark(player)) return;
        this.consumeActive(player, 1);
        break;
      }

      case 'wedge': {
        const door = this.doorWithinReach(player);
        // No door, no wedge — and crucially the stick is not spent. Losing a wedge to a
        // mistimed keypress in a corridor would make the item feel like a trap.
        if (door === -1) return;
        this.wedgedDoors.add(door);
        // The navigator already knows how to make a door impassable; the descent uses the
        // same call. The difference is that a wedge does *not* touch the tile grid, so it
        // stops entities and lets players walk straight through — which is the entire item.
        this.nav.sealDoor(door);
        this.consumeActive(player, 1);
        break;
      }

      case 'detector': {
        player.emfOn = !player.emfOn;
        // Ping straight away rather than after a delay: switching it on and hearing
        // nothing for a second reads as a broken device, not as an empty corridor.
        player.emfPingIn = 0;
        break;
      }
    }

    player.useCooldown = spec.cooldown;
    this.emitSound(spec.useSound, player.state.x, player.state.z, spec.noiseOnUse);
    // The sound is atmosphere; this is the gameplay. Reaching into the backpack is never
    // free — the Blind One hunts this field, and it feeds the descent.
    const tile = worldToTile(this.level, player.state.x, player.state.z);
    this.noise.emit(tile.x, tile.y, spec.noiseOnUse);
  }

  dropActiveItem(player: ServerPlayer): void {
    if (player.downed || !this.isAlive(player) || player.escaped) return;
    const slot = player.inventory[player.activeSlot];
    if (!slot) return;

    // The whole stack goes down at once: dropping one of four glowsticks and leaving three
    // behind is fiddly, and there is never a moment in a chase when it is what you meant.
    this.spawnWorldItem(slot.item, player.state.x, player.state.z, slot.count, 0);
    player.inventory[player.activeSlot] = null;
    player.inventoryDirty = true;

    this.emitSound('item.drop', player.state.x, player.state.z, NOISE_ITEM_DROP);
    const tile = worldToTile(this.level, player.state.x, player.state.z);
    this.noise.emit(tile.x, tile.y, NOISE_ITEM_DROP);
  }

  /**
   * Puts `count` of an item into a free slot, stacking where the spec allows it.
   * Returns how many did not fit — a full backpack is a real refusal, not a silent loss.
   */
  giveItem(player: ServerPlayer, item: string, count: number): number {
    const spec = getItemSpec(item);
    if (!spec) return count;
    let left = count;

    if (spec.stack > 1) {
      for (const slot of player.inventory) {
        if (left <= 0) break;
        if (!slot || slot.item !== item) continue;
        const room = spec.stack - slot.count;
        const moved = Math.min(room, left);
        slot.count += moved;
        left -= moved;
      }
    }

    for (let i = 0; i < player.inventory.length && left > 0; i++) {
      if (player.inventory[i]) continue;
      const moved = Math.min(spec.stack, left);
      player.inventory[i] = { item, count: moved };
      left -= moved;
    }

    if (left !== count) player.inventoryDirty = true;
    return left;
  }

  private consumeActive(player: ServerPlayer, count: number): void {
    const slot = player.inventory[player.activeSlot];
    if (!slot) return;
    slot.count -= count;
    if (slot.count <= 0) player.inventory[player.activeSlot] = null;
    player.inventoryDirty = true;
  }

  private spawnWorldItem(item: string, x: number, z: number, count: number, burnSeconds: number): void {
    const spec = getItemSpec(item);
    this.worldItems.push({
      id: this.nextWorldItemId++,
      item,
      x,
      z,
      count,
      // Only something that actually glows counts as lit. A decoy is active for the same
      // twenty-five seconds and is not a light — it is the opposite kind of beacon.
      lit: burnSeconds > 0 && spec?.lightRange !== undefined,
      burnLeft: burnSeconds,
      pulseIn: spec?.noiseEvery ?? 0,
    });
    this.worldItemsDirty = true;
  }

  /**
   * The nearest doorway within arm's reach, or -1.
   *
   * Doors are single tiles, so this is a scan over the door list rather than a raycast.
   * There are never more than a few hundred and this runs on a keypress, not per tick.
   */
  private doorWithinReach(player: ServerPlayer): number {
    let best = -1;
    let bestDistance = INTERACT_RANGE;
    for (const door of this.level.doors) {
      if (this.wedgedDoors.has(door.id)) continue;
      const world = tileToWorld(this.level, door.x, door.y);
      const distance = Math.hypot(world.x - player.state.x, world.z - player.state.z);
      if (distance > bestDistance) continue;
      best = door.id;
      bestDistance = distance;
    }
    return best;
  }

  /**
   * Draws a chalk mark on the wall the player is closest to.
   *
   * If there is no wall in reach the mark goes on the floor where they stand. Refusing to
   * draw in the middle of a hall would be technically correct and infuriating: the point of
   * the item is to answer "have I been here before", and that question gets asked in halls.
   */
  private drawMark(player: ServerPlayer): boolean {
    const tile = worldToTile(this.level, player.state.x, player.state.z);
    const neighbours: [number, number, number][] = [
      [1, 0, Math.PI / 2],
      [-1, 0, -Math.PI / 2],
      [0, 1, 0],
      [0, -1, Math.PI],
    ];

    for (const [dx, dy, yaw] of neighbours) {
      const nx = tile.x + dx;
      const ny = tile.y + dy;
      if (nx < 0 || ny < 0 || nx >= this.level.width || ny >= this.level.height) continue;
      if (this.grid.tiles[ny * this.grid.width + nx] === FLOOR) continue;

      const wall = tileToWorld(this.level, nx, ny);
      const here = tileToWorld(this.level, tile.x, tile.y);
      this.marks.push({
        id: this.nextMarkId++,
        // Just short of the wall face, so the mark is on the plaster rather than inside it.
        x: here.x + (wall.x - here.x) * 0.42,
        z: here.z + (wall.z - here.z) * 0.42,
        yaw,
        by: player.id,
      });
      this.marksDirty = true;
      return true;
    }

    this.marks.push({ id: this.nextMarkId++, x: player.state.x, z: player.state.z, yaw: 0, by: player.id });
    this.marksDirty = true;
    return true;
  }

  /** Burns down placeables, lets decoys shout, and clears the ones that have gone out. */
  private stepItems(dt: number): void {
    let changed = false;
    for (let i = this.worldItems.length - 1; i >= 0; i--) {
      const world = this.worldItems[i];
      if (world.burnLeft <= 0) continue;
      world.burnLeft -= dt;

      const spec = getItemSpec(world.item);
      if (spec?.noiseEvery && spec.noisePulse) {
        world.pulseIn -= dt;
        if (world.pulseIn <= 0) {
          world.pulseIn = spec.noiseEvery;
          // The decoy's whole job: be the loudest thing in the building, somewhere you are
          // not. It goes into the same field the player's own footsteps do, so an entity
          // cannot tell the difference — which is the point.
          const tile = worldToTile(this.level, world.x, world.z);
          this.noise.emit(tile.x, tile.y, spec.noisePulse);
          this.emitSound('item.decoyBeep', world.x, world.z, spec.noisePulse);
        }
      }

      if (world.burnLeft <= 0) {
        this.worldItems.splice(i, 1);
        changed = true;
      }
    }
    if (changed) this.worldItemsDirty = true;
  }

  /**
   * The EMF detector: a box that makes noise about noise.
   *
   * It never draws a meter. It beeps, faster the closer something is, and every beep is
   * itself audible in the noise field — so the tool that tells you something is there also
   * tells the something that you are. That trade is the item.
   */
  private stepDetectors(players: ServerPlayer[], dt: number): void {
    for (const player of players) {
      if (!player.emfOn) continue;
      if (!this.isAlive(player) || player.escaped) {
        player.emfOn = false;
        continue;
      }

      const spec = getItemSpec('emf')!;
      const range = spec.detectRange ?? 20;
      let nearest = Infinity;
      for (const entity of this.entities) {
        nearest = Math.min(nearest, Math.hypot(entity.x - player.state.x, entity.z - player.state.z));
      }

      player.emfPingIn -= dt;
      if (player.emfPingIn > 0) continue;

      if (nearest > range) {
        // Nothing in range still ticks, slowly. Silence would be indistinguishable from a
        // flat battery, and a player who cannot trust the tool will not carry it.
        player.emfPingIn = EMF_PING_SLOW * 1.6;
        this.emitSound('item.emfPing', player.state.x, player.state.z, 1);
        continue;
      }

      const closeness = clamp01(1 - nearest / range);
      player.emfPingIn = EMF_PING_SLOW + (EMF_PING_FAST - EMF_PING_SLOW) * closeness;
      this.emitSound('item.emfPing', player.state.x, player.state.z, EMF_PING_NOISE);
      const tile = worldToTile(this.level, player.state.x, player.state.z);
      this.noise.emit(tile.x, tile.y, EMF_PING_NOISE);
    }
  }

  /** The world item within reach, nearest first, or null. */
  private nearestWorldItem(player: ServerPlayer): WorldItem | null {
    let best: WorldItem | null = null;
    let bestDistance = INTERACT_RANGE;
    for (const world of this.worldItems) {
      const distance = Math.hypot(world.x - player.state.x, world.z - player.state.z);
      if (distance > bestDistance) continue;
      best = world;
      bestDistance = distance;
    }
    return best;
  }

  private tryPickup(player: ServerPlayer): boolean {
    const world = this.nearestWorldItem(player);
    if (!world) return false;

    const left = this.giveItem(player, world.item, world.count);
    if (left === world.count) return false; // Backpack full: it stays on the floor.

    if (left > 0) {
      world.count = left;
    } else {
      this.worldItems.splice(this.worldItems.indexOf(world), 1);
    }
    this.worldItemsDirty = true;
    this.emitSound('item.pickup', world.x, world.z, 3);
    return true;
  }

  private handleInteract(player: ServerPlayer, players: ServerPlayer[]): void {
    if (player.downed || !this.isAlive(player) || player.escaped) return;

    // Reviving a teammate outranks everything else within reach.
    const downedMate = players.find(
      (p) =>
        p.id !== player.id &&
        p.downed &&
        this.isAlive(p) &&
        this.distance(player, p) <= INTERACT_RANGE,
    );
    if (downedMate) {
      downedMate.reviveProgress = Math.max(downedMate.reviveProgress, 0.0001);
      return;
    }

    for (const placement of this.level.objectives) {
      const state = this.objectives[placement.id];
      const world = tileToWorld(this.level, placement.x, placement.y);
      const dx = world.x - player.state.x;
      const dz = world.z - player.state.z;
      if (Math.sqrt(dx * dx + dz * dz) > INTERACT_RANGE) continue;

      if (placement.kind === 'fuse' && !state.done && state.carriedBy === -1 && player.carrying === -1) {
        state.carriedBy = player.id;
        player.carrying = placement.id;
        this.objectivesDirty = true;
        this.emitSound('objective.fusePickup', world.x, world.z, 5);
        return;
      }

      if (placement.kind === 'generator' && player.carrying !== -1) {
        const fuse = this.objectives[player.carrying];
        fuse.done = true;
        fuse.carriedBy = -1;
        player.carrying = -1;
        this.objectivesDirty = true;
        this.emitSound('objective.fuseInsert', world.x, world.z, 9);

        if (this.fusesCollected >= this.fuseTotal) {
          const generator = this.objectives.find((o) => o.kind === 'generator')!;
          const exit = this.objectives.find((o) => o.kind === 'exit')!;
          generator.done = true;
          exit.done = true;
          this.emitSound('objective.exitOpen', world.x, world.z, 16);
          // Powering the exit is the loudest thing that happens in a run, and it wakes
          // the level up. Progress is punished; that is the whole tension curve.
          this.descent = Math.min(1, this.descent + 0.08);
        }
        return;
      }

      if (placement.kind === 'exit' && this.objectives[placement.id].done) {
        player.escaped = true;
        this.emitSound('objective.extract', world.x, world.z, 6);
        return;
      }
    }

    // Loot is checked last, after every objective has had its chance. Dropping a glowstick
    // on top of a fuse must never make the fuse unpickable — losing an objective to your
    // own tidiness would be an unfixable run, and no amount of proximity weighting is worth
    // that risk when ordering solves it outright.
    this.tryPickup(player);
  }

  // ---------------------------------------------------------------------------
  // Descent
  // ---------------------------------------------------------------------------

  private stepDescent(players: ServerPlayer[], dt: number): void {
    let totalNoise = 0;
    let brightness = 0;
    let counted = 0;
    for (const player of players) {
      if (!this.isAlive(player) || player.escaped) continue;
      totalNoise += player.state.noise;
      const tile = worldToTile(this.level, player.state.x, player.state.z);
      brightness += lightAt(this.lightField, this.level.width, tile.x, tile.y);
      counted++;
    }
    const avgBrightness = counted > 0 ? brightness / counted : 0;

    const base = dt / this.theme.descentSeconds;
    const noiseFactor = 1 + totalNoise * 0.055;
    const objectiveFactor = 1 + this.fusesCollected * 0.32;
    // Standing in bright light genuinely slows the rot. It gives players something to do
    // with the light beyond seeing, and makes the darkening feel earned rather than timed.
    const lightFactor = avgBrightness > 0.55 ? 0.7 : 1;

    this.descent = clamp01(this.descent + base * noiseFactor * objectiveFactor * lightFactor);
    this.peakDescent = Math.max(this.peakDescent, this.descent);

    while (
      this.descentIndex < this.level.descent.length &&
      this.level.descent[this.descentIndex].at <= this.descent
    ) {
      this.applyDescentEvent(this.descentIndex);
      this.descentIndex++;
    }
  }

  private applyDescentEvent(index: number): void {
    const event = this.level.descent[index];
    switch (event.kind) {
      case 'lightsOut': {
        for (const room of event.rooms) this.darkRooms.add(room);
        // Only the light field is recomputed; the geometry never changes for this event.
        this.lightField = propagateLight(this.grid, this.level.fixtures, this.darkRooms);
        break;
      }
      case 'seal':
        this.nav.sealDoor(event.door);
        break;
      case 'themeShift':
        // Purely visual on the client; the server just relays the index.
        break;
    }
    this.outbox.push({ t: 'descentEvent', index, tick: this.tick });
  }

  // ---------------------------------------------------------------------------
  // Entities
  // ---------------------------------------------------------------------------

  private stepEntities(players: ServerPlayer[], dt: number): void {
    this.director.update(dt);
    this.spawnCooldown -= dt;

    const views: AiPlayerView[] = players.map((p) => {
      const tile = worldToTile(this.level, p.state.x, p.state.z);
      return {
        id: p.id,
        x: p.state.x,
        z: p.state.z,
        tileX: clamp(tile.x, 0, this.level.width - 1),
        tileY: clamp(tile.y, 0, this.level.height - 1),
        alive: this.isAlive(p) && !p.escaped && p.connected,
        downed: p.downed,
        crouching: p.state.crouching,
        yaw: p.state.yaw,
        flashlightOn: p.flashlightOn,
        focusBeam: p.focusBeam,
      };
    });

    if (this.spawnCooldown <= 0) {
      this.trySpawn(views);
      this.spawnCooldown = 12;
    }

    // Everything currently giving off light, in the swarm's terms. Same `lit` distinction
    // the renderer uses: a glowstick still in your backpack, or one lying on the floor
    // uncracked, is not a light and pulls nothing toward it.
    const lights: AiLight[] = [];
    for (const item of this.worldItems) {
      if (!item.lit) continue;
      const spec = getItemSpec(item.item);
      if (spec?.lightRange === undefined) continue;
      lights.push({ x: item.x, z: item.z, strength: spec.lightRange });
    }
    for (const player of players) {
      if (!player.flashlightOn || !this.isAlive(player) || player.escaped) continue;
      lights.push({ x: player.state.x, z: player.state.z, strength: player.focusBeam ? 9 : 6 });
    }

    const world: AiWorld = {
      level: this.level,
      grid: this.grid,
      nav: this.nav,
      noise: this.noise,
      lightField: this.lightField,
      players: views,
      lights,
      descent: this.descent,
      director: this.director,
      random: () => this.rng.next(),
      emitSound: (key, x, z, loudness) => this.emitSound(key, x, z, loudness),
      damagePlayer: (playerId, amount, byEntity) => this.damagePlayer(players, playerId, amount, byEntity),
      onScare: (entityId, kind, intensity) => {
        this.outbox.push({ t: 'scare', kind, entityId, intensity });
      },
    };

    for (const entity of this.entities) updateEntity(entity, world, dt);
  }

  private trySpawn(views: AiPlayerView[]): void {
    for (const roster of this.theme.entities) {
      if (this.descent < roster.wakesAt) continue;
      const live = this.entities.filter((e) => e.kind === roster.kind).length;
      if (live >= roster.max) continue;

      // Never appear on top of someone. A monster that materialises in your face is a
      // cheap shock; one you hear coming from three rooms away is dread.
      for (let attempt = 0; attempt < 48; attempt++) {
        const tile = this.nav.randomReachableTile(() => this.rng.next());
        if (!tile) continue;
        const world = tileToWorld(this.level, tile.x, tile.y);
        const tooClose = views.some((p) => {
          if (!p.alive) return false;
          const dx = p.x - world.x;
          const dz = p.z - world.z;
          return Math.sqrt(dx * dx + dz * dz) < MIN_SPAWN_DISTANCE;
        });
        if (tooClose) continue;

        const spec = getEntitySpec(roster.kind);
        // Swarms arrive together. `roster.max` still counts bodies rather than groups, so
        // the ceiling in the theme file means what it looks like it means.
        const group = spec.swarm ? Math.min(spec.swarm.group, roster.max - live) : 1;
        for (let n = 0; n < group; n++) {
          // Fan them a little so five bodies do not start inside one another; collision
          // resolution would untangle them, but not before one frame of them overlapping.
          const spot =
            n === 0
              ? tile
              : { x: tile.x + ((n % 3) - 1), y: tile.y + ((Math.floor(n / 3) % 3) - 1) };
          const place = this.nav.passable(spot.x, spot.y) ? spot : tile;
          this.entities.push(createEntity(this.nextEntityId++, roster.kind, spec, place, this.level));
        }
        return;
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Survival: sanity, battery, bleeding out, revives
  // ---------------------------------------------------------------------------

  private stepSurvival(players: ServerPlayer[], dt: number): void {
    for (const player of players) {
      if (!this.isAlive(player) || player.escaped) continue;

      if (player.useCooldown > 0) player.useCooldown = Math.max(0, player.useCooldown - dt);

      if (player.flashlightOn) {
        const drain =
          (player.focusBeam ? FLASHLIGHT_DRAIN_FOCUS : FLASHLIGHT_DRAIN_WIDE) * batteryFactor(player.perks);
        player.battery = Math.max(0, player.battery - drain * dt);
        if (player.battery <= 0) {
          player.flashlightOn = false;
          this.emitSound('item.batteryDead', player.state.x, player.state.z, 3);
        }
      }

      const tile = worldToTile(this.level, player.state.x, player.state.z);
      const brightness = lightAt(this.lightField, this.level.width, tile.x, tile.y);
      const litByOwnLamp = player.flashlightOn ? 0.45 : 0;
      const effectiveLight = Math.max(brightness, litByOwnLamp);

      let sanityDelta = effectiveLight > 0.35 ? SANITY_LIGHT_REGEN * effectiveLight : -SANITY_DARK_DRAIN * (1 - effectiveLight);

      // Being watched is far worse than being in the dark.
      const hunted = this.entities.some(
        (e) =>
          (e.state === AiState.Hunting || e.state === AiState.Lunge) &&
          Math.hypot(e.x - player.state.x, e.z - player.state.z) < 14,
      );
      if (hunted) sanityDelta -= SANITY_SEEN_DRAIN;

      const buddy = players.some(
        (p) => p.id !== player.id && this.isAlive(p) && !p.downed && this.distance(player, p) < SANITY_BUDDY_RANGE,
      );
      if (buddy) sanityDelta += SANITY_BUDDY_REGEN;

      player.sanity = clamp(player.sanity + sanityDelta * dt, 0, SANITY_MAX);

      if (player.downed) {
        if (player.reviveProgress > 0) {
          // The medic perk belongs to whoever is doing the lifting, not to the body on the
          // floor. Reading `player.perks` here would be silently backwards: a medic who went
          // down would heal *themselves* faster, which is the one situation the perk is not
          // about. Fall back to the plain duration when nobody is in reach — the lapse check
          // further down will end the channel on the next pass anyway.
          const helper = players.find(
            (p) => p.id !== player.id && !p.downed && this.isAlive(p) && this.distance(player, p) <= INTERACT_RANGE,
          );
          player.reviveProgress += dt / (REVIVE_SECONDS * reviveFactor(helper?.perks ?? {}));
          if (player.reviveProgress >= 1) {
            player.downed = false;
            player.reviveProgress = 0;
            player.hp = Math.round(PLAYER_MAX_HP * 0.45);
            player.bleedout = DOWNED_BLEEDOUT_SECONDS;
            this.outbox.push({ t: 'playerRevived', playerId: player.id, by: -1 });
            this.emitSound('player.revived', player.state.x, player.state.z, 5);
          }
        } else {
          player.bleedout -= dt;
          if (player.bleedout <= 0) {
            player.hp = 0;
            this.dropCarried(player);
            this.emitSound('player.died', player.state.x, player.state.z, 7);
          }
        }
      }

      if (player.connected) player.disconnectedFor = 0;
      else player.disconnectedFor += dt;
    }

    // Revive channels lapse if the reviver walks away or goes down themselves.
    for (const player of players) {
      if (!player.downed || player.reviveProgress <= 0) continue;
      const helper = players.some(
        (p) => p.id !== player.id && !p.downed && this.isAlive(p) && this.distance(player, p) <= INTERACT_RANGE,
      );
      if (!helper) player.reviveProgress = 0;
    }
  }

  private damagePlayer(players: ServerPlayer[], playerId: number, amount: number, byEntity: number): void {
    const player = players.find((p) => p.id === playerId);
    if (!player || !this.isAlive(player) || player.escaped) return;
    if (player.downed) {
      player.hp = 0;
      this.dropCarried(player);
      return;
    }
    player.hp = Math.max(0, player.hp - amount);
    this.emitSound('player.hurt', player.state.x, player.state.z, 6);
    if (player.hp <= 0) {
      player.downed = true;
      player.hp = 1;
      player.bleedout = DOWNED_BLEEDOUT_SECONDS;
      player.reviveProgress = 0;
      this.dropCarried(player);
      this.outbox.push({ t: 'playerDown', playerId: player.id, by: byEntity });
    }
  }

  private dropCarried(player: ServerPlayer): void {
    if (player.carrying === -1) return;
    const state = this.objectives[player.carrying];
    state.carriedBy = -1;
    // The fuse stays where it was picked up rather than dropping at the corpse: hunting
    // for a dropped objective in a level that has gone dark is not fun, it is a punishment
    // on top of a punishment.
    player.carrying = -1;
    this.objectivesDirty = true;
  }

  private checkOutcome(players: ServerPlayer[]): void {
    const participants = players.filter((p) => p.connected || p.disconnectedFor < 90);
    if (participants.length === 0) return;
    const anyPlaying = participants.some((p) => this.isAlive(p) && !p.escaped);
    if (anyPlaying) return;

    const survivors = participants.filter((p) => p.escaped).length;
    this.outcome = survivors > 0 ? 'extracted' : 'wipe';
  }

  stats(players: ServerPlayer[]): RunStats {
    const survivors = players.filter((p) => p.escaped).length;
    const fuses = this.fusesCollected;
    return {
      fusesCollected: fuses,
      fusesTotal: this.fuseTotal,
      survivors,
      durationSeconds: Math.round(this.elapsed),
      peakDescent: this.peakDescent,
      reward: Math.round(fuses * 40 + survivors * 90 + this.peakDescent * 60),
    };
  }

  isAlive(player: ServerPlayer): boolean {
    return player.hp > 0;
  }

  private distance(a: ServerPlayer, b: ServerPlayer): number {
    return Math.hypot(a.state.x - b.state.x, a.state.z - b.state.z);
  }

  private emitSound(key: string, x: number, z: number, loudness: number): void {
    this.outbox.push({ t: 'sound', key, x, z, loudness });
  }

  /** Entity records for the snapshot, plus the flags the client renders them with. */
  entitySnapshots(): {
    id: number;
    kind: number;
    flags: number;
    x: number;
    z: number;
    yaw: number;
    aiState: number;
    hp: number;
  }[] {
    return this.entities.map((e) => ({
      id: e.id,
      kind: EntityKind.Blind,
      flags: EntityFlags.Alive,
      x: e.x,
      z: e.z,
      yaw: e.yaw,
      aiState: e.state,
      hp: e.hp,
    }));
  }

  playerFlags(player: ServerPlayer): number {
    let flags = 0;
    if (this.isAlive(player) && !player.escaped) flags |= EntityFlags.Alive;
    if (player.state.crouching) flags |= EntityFlags.Crouching;
    if (player.downed) flags |= EntityFlags.Downed;
    if (!player.connected) flags |= EntityFlags.Disconnected;
    if (player.flashlightOn) flags |= EntityFlags.FlashlightOn;
    if (player.focusBeam) flags |= EntityFlags.FocusedBeam;
    return flags;
  }

  /** Copy used by tests to compare against a client-side prediction. */
  snapshotPlayerState(player: ServerPlayer): PlayerSimState {
    return clonePlayerState(player.state);
  }
}
