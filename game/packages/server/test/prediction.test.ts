/**
 * Client prediction under a hostile network.
 *
 * The transport is a WebSocket, so the network model here is TCP's: delivery is ordered
 * and lossless, and congestion shows up as *delay* — including head-of-line blocking,
 * where one late packet holds up everything queued behind it. Modelling dropped or
 * reordered packets would be modelling UDP, and would fail this test for reasons that
 * cannot occur in the real game.
 *
 * What is asserted is the property the netcode rests on: replaying unacknowledged inputs
 * from an authoritative position reproduces the server's simulation exactly, so the
 * correction that reaches the camera is nothing at all.
 */

import { describe, expect, it } from 'vitest';
import { generateLevel, levelGrid, tileToWorld, type GridView } from '@game/shared/levelgen';
import { Rng } from '@game/shared/prng';
import {
  Buttons,
  DT,
  SNAPSHOT_EVERY,
  clonePlayerState,
  copyPlayerState,
  createPlayerState,
  stepPlayer,
  type Input,
  type PlayerSimState,
} from '@game/shared/sim';

interface Packet<T> {
  arrivesAtTick: number;
  payload: T;
}

interface Snapshot {
  x: number;
  z: number;
  lastProcessedSeq: number;
}

interface Options {
  latencyTicks: number;
  jitterTicks: number;
  /** Chance of a delay spike, standing in for a TCP retransmission. */
  stallChance: number;
  stallTicks: number;
  ticks: number;
}

/** An ordered link: nothing arrives before what was sent ahead of it. */
class Link<T> {
  private readonly queue: Packet<T>[] = [];
  private lastArrival = -1;

  constructor(
    private readonly rng: Rng,
    private readonly options: Options,
  ) {}

  send(tick: number, payload: T): void {
    let arrival = tick + this.options.latencyTicks + this.rng.int(0, this.options.jitterTicks);
    if (this.rng.chance(this.options.stallChance)) arrival += this.options.stallTicks;
    arrival = Math.max(arrival, this.lastArrival + 1);
    this.lastArrival = arrival;
    this.queue.push({ arrivesAtTick: arrival, payload });
  }

  receive(tick: number): T[] {
    const out: T[] = [];
    while (this.queue.length > 0 && this.queue[0].arrivesAtTick <= tick) {
      out.push(this.queue.shift()!.payload);
    }
    return out;
  }
}

interface Result {
  /** Largest single correction applied to the predicted position, in metres. */
  maxCorrection: number;
  /** Divergence once every input in flight has been consumed. */
  settledError: number;
}

function simulate(grid: GridView, start: { x: number; z: number }, options: Options): Result {
  const rng = new Rng('net');

  const client = createPlayerState(start.x, start.z);
  const server = createPlayerState(start.x, start.z);
  const history: { input: Input; state: PlayerSimState }[] = [];

  const upstream = new Link<Input[]>(rng, options);
  const downstream = new Link<Snapshot>(rng, options);
  const serverQueue: Input[] = [];
  let serverLastSeq = 0;
  let seq = 1;
  let yaw = 0;
  let maxCorrection = 0;
  let sinceSend = 0;
  let lastReconciledSeq = 0;

  const acceptUpstream = (tick: number): void => {
    for (const batch of upstream.receive(tick)) {
      for (const arrived of batch) {
        // Redundant copies of already-consumed or already-queued inputs are expected.
        if (arrived.seq <= serverLastSeq) continue;
        if (serverQueue.some((queued) => queued.seq === arrived.seq)) continue;
        serverQueue.push(arrived);
      }
    }
    serverQueue.sort((a, b) => a.seq - b.seq);
  };

  const stepServer = (): void => {
    const next = serverQueue.shift();
    if (!next) return; // Mirrors the server: no input, no step.
    stepPlayer(server, next, grid, DT);
    serverLastSeq = next.seq;
  };

  for (let tick = 0; tick < options.ticks; tick++) {
    if (tick % 37 === 0) yaw = rng.range(-Math.PI, Math.PI);
    const buttons =
      Buttons.Forward | (tick % 120 < 60 ? Buttons.Sprint : 0) | (tick % 400 < 40 ? Buttons.Crouch : 0);
    const input: Input = { seq: seq++, buttons, yaw, pitch: 0, slot: 0 };
    stepPlayer(client, input, grid, DT);
    history.push({ input, state: clonePlayerState(client) });

    // Send the tail of the unacknowledged history, exactly as the client does.
    sinceSend++;
    if (sinceSend >= SNAPSHOT_EVERY) {
      sinceSend = 0;
      upstream.send(
        tick,
        history.slice(-6).map((entry) => entry.input),
      );
    }

    acceptUpstream(tick);
    stepServer();

    if (tick % SNAPSHOT_EVERY === 0) {
      downstream.send(tick, { x: server.x, z: server.z, lastProcessedSeq: serverLastSeq });
    }

    for (const snapshot of downstream.receive(tick)) {
      // Nothing new acknowledged: the server consumed no input, so its position has not
      // moved and replaying again would advance the prediction twice.
      if (snapshot.lastProcessedSeq <= lastReconciledSeq) continue;
      lastReconciledSeq = snapshot.lastProcessedSeq;

      // Captured before the rewind: the correction is the difference between where the
      // player already appeared to be and where they end up after replay. Sampling this
      // after the rewind would measure the rewind itself, which is not something the
      // player ever sees.
      const beforeX = client.x;
      const beforeZ = client.z;

      const acked = history.findIndex((entry) => entry.input.seq === snapshot.lastProcessedSeq);
      if (acked >= 0) copyPlayerState(history[acked].state, client);
      while (history.length > 0 && history[0].input.seq <= snapshot.lastProcessedSeq) history.shift();

      client.x = snapshot.x;
      client.z = snapshot.z;
      for (const entry of history) {
        stepPlayer(client, entry.input, grid, DT);
        copyPlayerState(client, entry.state);
      }
      if (tick > 120) {
        maxCorrection = Math.max(maxCorrection, Math.hypot(client.x - beforeX, client.z - beforeZ));
      }
    }
  }

  // Drain what is still in flight, so both simulations are compared at the same point in
  // the input sequence rather than at two different ones.
  for (let extra = 0; extra < 4000 && serverLastSeq < seq - 1; extra++) {
    const tick = options.ticks + extra;
    acceptUpstream(tick);
    stepServer();
  }

  return { maxCorrection, settledError: Math.hypot(server.x - client.x, server.z - client.z) };
}

function openArena(size = 48): GridView {
  const grid: GridView = { width: size, height: size, tiles: new Uint8Array(size * size).fill(1) };
  for (let i = 0; i < size; i++) {
    grid.tiles[i] = 0;
    grid.tiles[(size - 1) * size + i] = 0;
    grid.tiles[i * size] = 0;
    grid.tiles[i * size + size - 1] = 0;
  }
  return grid;
}

const PERFECT: Options = { latencyTicks: 0, jitterTicks: 0, stallChance: 0, stallTicks: 0, ticks: 1800 };
const TYPICAL: Options = { latencyTicks: 5, jitterTicks: 2, stallChance: 0.02, stallTicks: 6, ticks: 3600 };
const BAD: Options = { latencyTicks: 12, jitterTicks: 6, stallChance: 0.06, stallTicks: 18, ticks: 5400 };

describe('prediction and reconciliation', () => {
  it('is exact on a perfect connection', () => {
    const result = simulate(openArena(), { x: 0, z: 0 }, PERFECT);
    expect(result.maxCorrection).toBeLessThan(1e-9);
    expect(result.settledError).toBeLessThan(1e-9);
  });

  it('stays exact through 80 ms of latency, jitter and occasional stalls', () => {
    const result = simulate(openArena(), { x: 0, z: 0 }, TYPICAL);
    expect(result.maxCorrection).toBeLessThan(1e-9);
    expect(result.settledError).toBeLessThan(1e-9);
  });

  it('stays exact on a bad connection inside a real level', () => {
    const level = generateLevel('predict', 'level0');
    const grid = levelGrid(level);
    const spawn = tileToWorld(level, level.spawn.x, level.spawn.y);
    const result = simulate(grid, spawn, BAD);
    // Convergence here is not "close enough": replay reproduces the authoritative steps
    // bit for bit, so any drift at all would mean the two simulations had forked.
    expect(result.maxCorrection).toBeLessThan(1e-9);
    expect(result.settledError).toBeLessThan(1e-9);
  });
});
