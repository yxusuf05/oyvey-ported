/**
 * Binary encoding for the per-tick hot path.
 *
 * Snapshots are sent in full rather than delta-compressed against an acked baseline.
 * With a four-player cap the entire world is roughly fifty entities, so a full snapshot
 * is under a kilobyte and twenty of them a second cost ~12 kB/s — well below the point
 * where delta compression pays for the bugs it brings. The `baselineTick` field and the
 * client's ack are transmitted anyway, so delta encoding can be layered on later without
 * a protocol break.
 */

import { MessageTag } from './messages';
import type { Input } from '../sim/movement';

/** Positions are quantised to 1/128 m. The map is ±128 m, so this fits an int16 exactly. */
const POS_SCALE = 128;
const ANGLE_SCALE = 10000;

export interface EntitySnapshot {
  id: number;
  kind: number;
  flags: number;
  x: number;
  z: number;
  yaw: number;
  aiState: number;
  hp: number;
}

export interface SnapshotPayload {
  tick: number;
  baselineTick: number;
  lastProcessedInputSeq: number;
  descent: number;
  sanity: number;
  stamina: number;
  battery: number;
  hp: number;
  /** Seconds of bleedout left while downed. Zero when up — 60 fits in a byte. */
  bleedout: number;
  /** How far along a revive is, 0..100. */
  reviveProgress: number;
  entities: EntitySnapshot[];
}

export interface InputPayload {
  lastAckedTick: number;
  inputs: Input[];
}

const ENTITY_BYTES = 12;
const SNAPSHOT_HEADER_BYTES = 1 + 4 + 4 + 4 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 2;
const INPUT_RECORD_BYTES = 11;

export function encodeSnapshot(payload: SnapshotPayload): ArrayBuffer {
  const buffer = new ArrayBuffer(SNAPSHOT_HEADER_BYTES + payload.entities.length * ENTITY_BYTES);
  const view = new DataView(buffer);
  let o = 0;
  view.setUint8(o, MessageTag.Snapshot); o += 1;
  view.setUint32(o, payload.tick >>> 0); o += 4;
  view.setUint32(o, payload.baselineTick >>> 0); o += 4;
  view.setUint32(o, payload.lastProcessedInputSeq >>> 0); o += 4;
  view.setUint8(o, clampByte(payload.descent * 255)); o += 1;
  view.setUint8(o, clampByte(payload.sanity)); o += 1;
  view.setUint8(o, clampByte(payload.stamina)); o += 1;
  view.setUint8(o, clampByte(payload.battery)); o += 1;
  view.setUint8(o, clampByte(payload.hp)); o += 1;
  view.setUint8(o, clampByte(payload.bleedout)); o += 1;
  view.setUint8(o, clampByte(payload.reviveProgress)); o += 1;
  view.setUint16(o, payload.entities.length); o += 2;

  for (const e of payload.entities) {
    view.setUint16(o, e.id); o += 2;
    view.setUint8(o, e.kind); o += 1;
    view.setUint8(o, e.flags); o += 1;
    view.setInt16(o, clampInt16(Math.round(e.x * POS_SCALE))); o += 2;
    view.setInt16(o, clampInt16(Math.round(e.z * POS_SCALE))); o += 2;
    view.setInt16(o, clampInt16(Math.round(wrapAngle(e.yaw) * ANGLE_SCALE))); o += 2;
    view.setUint8(o, e.aiState); o += 1;
    view.setUint8(o, clampByte(e.hp)); o += 1;
  }
  return buffer;
}

export function decodeSnapshot(buffer: ArrayBuffer): SnapshotPayload {
  const view = new DataView(buffer);
  let o = 1; // tag already checked by the dispatcher
  const tick = view.getUint32(o); o += 4;
  const baselineTick = view.getUint32(o); o += 4;
  const lastProcessedInputSeq = view.getUint32(o); o += 4;
  const descent = view.getUint8(o) / 255; o += 1;
  const sanity = view.getUint8(o); o += 1;
  const stamina = view.getUint8(o); o += 1;
  const battery = view.getUint8(o); o += 1;
  const hp = view.getUint8(o); o += 1;
  const bleedout = view.getUint8(o); o += 1;
  const reviveProgress = view.getUint8(o); o += 1;
  const count = view.getUint16(o); o += 2;

  const entities: EntitySnapshot[] = new Array(count);
  for (let i = 0; i < count; i++) {
    const id = view.getUint16(o); o += 2;
    const kind = view.getUint8(o); o += 1;
    const flags = view.getUint8(o); o += 1;
    const x = view.getInt16(o) / POS_SCALE; o += 2;
    const z = view.getInt16(o) / POS_SCALE; o += 2;
    const yaw = view.getInt16(o) / ANGLE_SCALE; o += 2;
    const aiState = view.getUint8(o); o += 1;
    const entityHp = view.getUint8(o); o += 1;
    entities[i] = { id, kind, flags, x, z, yaw, aiState, hp: entityHp };
  }

  return {
    tick,
    baselineTick,
    lastProcessedInputSeq,
    descent,
    sanity,
    stamina,
    battery,
    hp,
    bleedout,
    reviveProgress,
    entities,
  };
}

/**
 * Inputs are batched: the client predicts at render rate but sends at 20 Hz, packing the
 * frames since the last send. Re-sending the still-unacked tail costs a few bytes and
 * makes a single dropped packet invisible.
 */
export function encodeInput(payload: InputPayload): ArrayBuffer {
  const count = Math.min(payload.inputs.length, 8);
  const buffer = new ArrayBuffer(1 + 4 + 1 + count * INPUT_RECORD_BYTES);
  const view = new DataView(buffer);
  let o = 0;
  view.setUint8(o, MessageTag.Input); o += 1;
  view.setUint32(o, payload.lastAckedTick >>> 0); o += 4;
  view.setUint8(o, count); o += 1;
  const start = payload.inputs.length - count;
  for (let i = 0; i < count; i++) {
    const input = payload.inputs[start + i];
    view.setUint32(o, input.seq >>> 0); o += 4;
    view.setUint16(o, input.buttons & 0xffff); o += 2;
    view.setInt16(o, clampInt16(Math.round(wrapAngle(input.yaw) * ANGLE_SCALE))); o += 2;
    view.setInt16(o, clampInt16(Math.round(input.pitch * ANGLE_SCALE))); o += 2;
    view.setUint8(o, input.slot & 0xff); o += 1;
  }
  return buffer;
}

export function decodeInput(buffer: ArrayBuffer): InputPayload {
  const view = new DataView(buffer);
  let o = 1;
  const lastAckedTick = view.getUint32(o); o += 4;
  const count = view.getUint8(o); o += 1;
  const inputs: Input[] = new Array(count);
  for (let i = 0; i < count; i++) {
    const seq = view.getUint32(o); o += 4;
    const buttons = view.getUint16(o); o += 2;
    const yaw = view.getInt16(o) / ANGLE_SCALE; o += 2;
    const pitch = view.getInt16(o) / ANGLE_SCALE; o += 2;
    const slot = view.getUint8(o); o += 1;
    inputs[i] = { seq, buttons, yaw, pitch, slot };
  }
  return { lastAckedTick, inputs };
}

export function messageTagOf(data: ArrayBuffer): number {
  return new DataView(data).getUint8(0);
}

/**
 * Wraps to (-PI, PI]. Applied before quantisation so a yaw that has accumulated many
 * turns still round-trips instead of saturating the int16.
 */
function wrapAngle(a: number): number {
  const TAU = Math.PI * 2;
  let v = a % TAU;
  if (v > Math.PI) v -= TAU;
  if (v <= -Math.PI) v += TAU;
  return v;
}

const clampByte = (v: number): number => (v < 0 ? 0 : v > 255 ? 255 : Math.round(v));
const clampInt16 = (v: number): number => (v < -32768 ? -32768 : v > 32767 ? 32767 : v);
