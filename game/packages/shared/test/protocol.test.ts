import { describe, expect, it } from 'vitest';
import { Rng } from '../src/prng';
import {
  MessageTag,
  decodeInput,
  decodeSnapshot,
  encodeInput,
  encodeSnapshot,
  messageTagOf,
  rleDecode,
  rleEncode,
  type EntitySnapshot,
  type SnapshotPayload,
} from '../src/protocol';
import type { Input } from '../src/sim';

/** Quantisation tolerances implied by the wire format. */
const POS_EPSILON = 1 / 128 / 2 + 1e-9;
const ANGLE_EPSILON = 1 / 10000 / 2 + 1e-9;

describe('binary codec', () => {
  it('round-trips snapshots for randomised worlds', () => {
    const rng = new Rng('codec');
    for (let iteration = 0; iteration < 400; iteration++) {
      const entities: EntitySnapshot[] = [];
      const count = rng.int(0, 40);
      for (let i = 0; i < count; i++) {
        entities.push({
          id: rng.int(0, 65535),
          kind: rng.int(0, 255),
          flags: rng.int(0, 255),
          x: rng.range(-127, 127),
          z: rng.range(-127, 127),
          yaw: rng.range(-Math.PI, Math.PI),
          aiState: rng.int(0, 7),
          hp: rng.int(0, 255),
        });
      }
      const payload: SnapshotPayload = {
        tick: rng.int(0, 2 ** 31),
        baselineTick: rng.int(0, 2 ** 31),
        lastProcessedInputSeq: rng.int(0, 2 ** 31),
        descent: rng.next(),
        sanity: rng.int(0, 100),
        stamina: rng.int(0, 100),
        battery: rng.int(0, 100),
        hp: rng.int(0, 100),
        entities,
      };

      const buffer = encodeSnapshot(payload);
      expect(messageTagOf(buffer)).toBe(MessageTag.Snapshot);
      const decoded = decodeSnapshot(buffer);

      expect(decoded.tick).toBe(payload.tick);
      expect(decoded.baselineTick).toBe(payload.baselineTick);
      expect(decoded.lastProcessedInputSeq).toBe(payload.lastProcessedInputSeq);
      expect(decoded.descent).toBeCloseTo(payload.descent, 2);
      expect(decoded.sanity).toBe(payload.sanity);
      expect(decoded.entities).toHaveLength(entities.length);

      decoded.entities.forEach((entity, i) => {
        const original = entities[i];
        expect(entity.id).toBe(original.id);
        expect(entity.kind).toBe(original.kind);
        expect(entity.flags).toBe(original.flags);
        expect(Math.abs(entity.x - original.x)).toBeLessThanOrEqual(POS_EPSILON);
        expect(Math.abs(entity.z - original.z)).toBeLessThanOrEqual(POS_EPSILON);
        expect(Math.abs(entity.yaw - original.yaw)).toBeLessThanOrEqual(ANGLE_EPSILON);
        expect(entity.aiState).toBe(original.aiState);
        expect(entity.hp).toBe(original.hp);
      });
    }
  });

  it('round-trips input batches', () => {
    const rng = new Rng('inputs');
    for (let iteration = 0; iteration < 400; iteration++) {
      const inputs: Input[] = [];
      const count = rng.int(1, 8);
      for (let i = 0; i < count; i++) {
        inputs.push({
          seq: rng.int(0, 2 ** 31),
          buttons: rng.int(0, 0xffff),
          yaw: rng.range(-Math.PI, Math.PI),
          pitch: rng.range(-Math.PI / 2, Math.PI / 2),
          slot: rng.int(0, 255),
        });
      }
      const lastAckedTick = rng.int(0, 2 ** 31);
      const decoded = decodeInput(encodeInput({ inputs, lastAckedTick }));

      expect(decoded.lastAckedTick).toBe(lastAckedTick);
      expect(decoded.inputs).toHaveLength(inputs.length);
      decoded.inputs.forEach((input, i) => {
        expect(input.seq).toBe(inputs[i].seq);
        expect(input.buttons).toBe(inputs[i].buttons);
        expect(Math.abs(input.yaw - inputs[i].yaw)).toBeLessThanOrEqual(ANGLE_EPSILON);
        expect(Math.abs(input.pitch - inputs[i].pitch)).toBeLessThanOrEqual(ANGLE_EPSILON);
        expect(input.slot).toBe(inputs[i].slot);
      });
    }
  });

  it('keeps only the most recent inputs when a batch overflows', () => {
    const inputs: Input[] = Array.from({ length: 20 }, (_, i) => ({
      seq: i,
      buttons: 0,
      yaw: 0,
      pitch: 0,
      slot: 0,
    }));
    const decoded = decodeInput(encodeInput({ inputs, lastAckedTick: 0 }));
    expect(decoded.inputs).toHaveLength(8);
    expect(decoded.inputs[decoded.inputs.length - 1].seq).toBe(19);
  });

  it('survives yaw values that have wrapped many turns', () => {
    const decoded = decodeInput(
      encodeInput({ inputs: [{ seq: 1, buttons: 0, yaw: 40 * Math.PI + 0.5, pitch: 0, slot: 0 }], lastAckedTick: 0 }),
    );
    // Without wrapping before quantisation this would saturate the int16 and freeze the view.
    expect(decoded.inputs[0].yaw).toBeCloseTo(0.5, 3);
  });
});

describe('level patch encoding', () => {
  it('round-trips a tile array', () => {
    const rng = new Rng('rle');
    const tiles = new Uint8Array(4096);
    // Long runs plus scattered noise, which is what a real maze looks like.
    for (let i = 0; i < tiles.length; i++) tiles[i] = rng.chance(0.7) ? 1 : 0;
    for (let i = 500; i < 900; i++) tiles[i] = 0;

    const encoded = rleEncode(tiles);
    expect(rleDecode(encoded, tiles.length)).toEqual(tiles);
  });

  it('compresses a mostly-uniform grid well', () => {
    const tiles = new Uint8Array(16384).fill(1);
    tiles[9000] = 0;
    // Three runs: ones, a zero, ones again.
    expect(rleEncode(tiles)).toHaveLength(6);
  });
});
