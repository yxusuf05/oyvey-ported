/**
 * Entity behaviour, asserted as rules a player could state out loud.
 *
 * The Blind One's whole design is one sentence — "it hunts by sound alone" — and these
 * tests are that sentence, mechanised. If crouching stops working, or standing still stops
 * working, the entity has silently become a random threat instead of a puzzle, and that is
 * a design regression a rendering screenshot would never catch.
 */

import { describe, expect, it } from 'vitest';
import { AiState } from '@game/shared/protocol';
import { bfsDistance, generateLevel, levelGrid, tileToWorld } from '@game/shared/levelgen';
import { BLIND_ONE } from '@game/shared/content';
import { Buttons, DT, type Input } from '@game/shared/sim';
import { createEntity } from '../src/ai/brains';
import { Run, type ServerPlayer } from '../src/sim/run';

const SEED = 'ai-fixture';

/** A reachable tile roughly `target` steps away from the spawn. */
function tileAtDistance(run: Run, target: number): { x: number; y: number } {
  const dist = bfsDistance(levelGrid(run.level), [run.level.spawn]);
  let best = { x: run.level.spawn.x, y: run.level.spawn.y };
  let bestDelta = Infinity;
  for (let i = 0; i < dist.length; i++) {
    if (dist[i] < 0) continue;
    const delta = Math.abs(dist[i] - target);
    if (delta < bestDelta) {
      bestDelta = delta;
      best = { x: i % run.level.width, y: Math.floor(i / run.level.width) };
    }
  }
  return best;
}

/** Ids must be unique: the director tracks active hunters in a set keyed by entity id. */
function spawnBlind(run: Run, tile: { x: number; y: number }, id = 1000): ReturnType<typeof createEntity> {
  const entity = createEntity(id, 'blind', BLIND_ONE, tile, run.level);
  run.entities.push(entity);
  return entity;
}

function feed(player: ServerPlayer, buttons: number, seq: number, yaw = 0): void {
  const input: Input = { seq, buttons, yaw, pitch: 0, slot: 0 };
  player.pending.push(input);
}

describe('The Blind One', () => {
  it('never hunts a player who crouches and stands still', () => {
    const run = new Run(SEED, 'level0');
    const player = run.createPlayer(1, 'Quiet');
    const entity = spawnBlind(run, tileAtDistance(run, 12));

    const states = new Set<number>();
    for (let tick = 0; tick < 60 * 30; tick++) {
      // Crouched and motionless: the one thing that is supposed to make you invisible to it.
      feed(player, Buttons.Crouch, tick + 1);
      run.step([player], DT);
      states.add(entity.state);
    }

    expect(states.has(AiState.Hunting)).toBe(false);
    expect(states.has(AiState.Lunge)).toBe(false);
    expect(player.hp).toBe(100);
  });

  it('hears a sprinting player and comes looking', () => {
    const run = new Run(SEED, 'level0');
    const player = run.createPlayer(1, 'Loud');
    const entity = spawnBlind(run, tileAtDistance(run, 8));

    let noticed = false;
    for (let tick = 0; tick < 60 * 25; tick++) {
      // Sprint back and forth so the player stays near the spawn but keeps making noise.
      const yaw = Math.floor(tick / 120) % 2 === 0 ? 0 : Math.PI;
      feed(player, Buttons.Forward | Buttons.Sprint, tick + 1, yaw);
      run.step([player], DT);
      if (entity.state === AiState.Investigate || entity.state === AiState.Hunting) noticed = true;
      if (noticed) break;
    }

    expect(noticed).toBe(true);
    expect(entity.awareness).toBeGreaterThan(0.3);
  });

  it('loses interest again once the noise stops', () => {
    const run = new Run(SEED, 'level0');
    const player = run.createPlayer(1, 'Loud');
    const entity = spawnBlind(run, tileAtDistance(run, 8));

    for (let tick = 0; tick < 60 * 8; tick++) {
      feed(player, Buttons.Forward | Buttons.Sprint, tick + 1);
      run.step([player], DT);
    }
    const peak = entity.awareness;

    for (let tick = 0; tick < 60 * 40; tick++) {
      feed(player, Buttons.Crouch, 60 * 8 + tick + 1);
      run.step([player], DT);
    }

    expect(peak).toBeGreaterThan(0.2);
    expect(entity.awareness).toBeLessThan(0.12);
    expect(entity.state).toBe(AiState.Patrol);
  });

  it('always telegraphs before it can do damage', () => {
    const run = new Run(SEED, 'level0');
    const player = run.createPlayer(1, 'Victim');
    const spawn = tileToWorld(run.level, run.level.spawn.x, run.level.spawn.y);
    const entity = spawnBlind(run, run.level.spawn);
    // Put it right on top of the player with full awareness — the worst case.
    entity.x = spawn.x + 0.6;
    entity.z = spawn.z;
    entity.awareness = 1.4;

    let lungeSeenAtTick = -1;
    let damageAtTick = -1;
    for (let tick = 0; tick < 60 * 10; tick++) {
      // The player holds still. A player who runs during the telegraph is supposed to get
      // away — that is what the warning is for — so this test deliberately does not.
      feed(player, Buttons.Crouch, tick + 1);
      run.step([player], DT);
      if (lungeSeenAtTick < 0 && entity.state === AiState.Lunge) lungeSeenAtTick = tick;
      if (damageAtTick < 0 && player.hp < 100) damageAtTick = tick;
      if (damageAtTick >= 0) break;
    }

    expect(lungeSeenAtTick).toBeGreaterThanOrEqual(0);
    expect(damageAtTick).toBeGreaterThanOrEqual(0);
    // The warning must be a real window, not a formality.
    const warningSeconds = (damageAtTick - lungeSeenAtTick) / 60;
    expect(warningSeconds).toBeGreaterThanOrEqual(BLIND_ONE.telegraphSeconds * 0.8);
  });
});

describe('the director', () => {
  it('caps concurrent hunters below the maximum for the current descent', () => {
    const run = new Run(SEED, 'level0');
    const player = run.createPlayer(1, 'Loud');
    for (let i = 0; i < 4; i++) spawnBlind(run, tileAtDistance(run, 6 + i * 2), 1000 + i);

    let maxConcurrent = 0;
    for (let tick = 0; tick < 60 * 40; tick++) {
      feed(player, Buttons.Forward | Buttons.Sprint, tick + 1);
      run.step([player], DT);
      const hunting = run.entities.filter((e) => e.state === AiState.Hunting || e.state === AiState.Lunge).length;
      maxConcurrent = Math.max(maxConcurrent, hunting);
    }

    expect(maxConcurrent).toBeGreaterThan(0);
    expect(maxConcurrent).toBeLessThanOrEqual(run.director.maxHunters(run.descent));
  });
});

describe('the run', () => {
  it('raises the descent faster when players make noise', () => {
    const quiet = new Run(SEED, 'level0');
    const quietPlayer = quiet.createPlayer(1, 'Quiet');
    const loud = new Run(SEED, 'level0');
    const loudPlayer = loud.createPlayer(1, 'Loud');

    for (let tick = 0; tick < 60 * 60; tick++) {
      quietPlayer.pending.push({ seq: tick + 1, buttons: Buttons.Crouch, yaw: 0, pitch: 0, slot: 0 });
      loudPlayer.pending.push({ seq: tick + 1, buttons: Buttons.Forward | Buttons.Sprint, yaw: 0, pitch: 0, slot: 0 });
      quiet.step([quietPlayer], DT);
      loud.step([loudPlayer], DT);
    }

    expect(loud.descent).toBeGreaterThan(quiet.descent);
  });

  it('ends the run when the only player dies', () => {
    const run = new Run(SEED, 'level0');
    const player = run.createPlayer(1, 'Doomed');
    player.hp = 0;
    run.step([player], DT);
    expect(run.outcome).toBe('wipe');
  });
});
