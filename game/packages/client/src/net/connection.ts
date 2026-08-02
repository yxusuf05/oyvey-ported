/**
 * WebSocket transport.
 *
 * Deliberately thin: it owns the socket, the hello handshake and message dispatch, and
 * nothing about the game. Prediction and interpolation live next to the simulation in
 * `game/`, because they need the level's collision grid to do their job.
 */

import {
  MessageTag,
  PROTOCOL_VERSION,
  decodeSnapshot,
  encodeInput,
  encodeJson,
  messageTagOf,
  type C2S,
  type S2C,
  type SnapshotPayload,
} from '@game/shared/protocol';
import type { Input } from '@game/shared/sim';

export type ConnectionState = 'idle' | 'connecting' | 'open' | 'closed';

type MessageHandler = (msg: S2C) => void;
type SnapshotHandler = (snapshot: SnapshotPayload) => void;
type StateHandler = (state: ConnectionState) => void;

export class Connection {
  private socket: WebSocket | null = null;
  private readonly messageHandlers = new Set<MessageHandler>();
  private readonly snapshotHandlers = new Set<SnapshotHandler>();
  private readonly stateHandlers = new Set<StateHandler>();

  state: ConnectionState = 'idle';
  playerId = -1;
  sessionToken = '';
  /** Smoothed round-trip time in milliseconds. */
  rtt = 0;

  private name = 'Anon';
  private pingTimer: ReturnType<typeof setInterval> | null = null;

  connect(name: string): Promise<void> {
    this.name = name;
    this.close();
    this.setState('connecting');

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${location.host}/ws`;

    return new Promise((resolve, reject) => {
      let socket: WebSocket;
      try {
        socket = new WebSocket(url);
      } catch (error) {
        this.setState('closed');
        reject(error);
        return;
      }
      socket.binaryType = 'arraybuffer';
      this.socket = socket;

      socket.addEventListener('open', () => {
        this.setState('open');
        this.send({ t: 'hello', protocol: PROTOCOL_VERSION, name: this.name, sessionToken: this.sessionToken || undefined });
        this.startPing();
        resolve();
      });

      socket.addEventListener('message', (event: MessageEvent) => {
        if (typeof event.data === 'string') {
          const msg = JSON.parse(event.data) as S2C;
          this.handleControl(msg);
          return;
        }
        const buffer = event.data as ArrayBuffer;
        if (messageTagOf(buffer) !== MessageTag.Snapshot) return;
        const snapshot = decodeSnapshot(buffer);
        for (const handler of this.snapshotHandlers) handler(snapshot);
      });

      socket.addEventListener('close', () => {
        this.stopPing();
        this.setState('closed');
      });

      socket.addEventListener('error', () => {
        if (this.state === 'connecting') reject(new Error('connection failed'));
      });
    });
  }

  private handleControl(msg: S2C): void {
    if (msg.t === 'welcome') {
      this.playerId = msg.playerId;
      this.sessionToken = msg.sessionToken;
    }
    if (msg.t === 'pong') {
      const sample = performance.now() - msg.sent;
      // Exponential smoothing: a single spike should not move the interpolation delay.
      this.rtt = this.rtt === 0 ? sample : this.rtt * 0.8 + sample * 0.2;
      return;
    }
    for (const handler of this.messageHandlers) handler(msg);
  }

  private startPing(): void {
    this.stopPing();
    this.pingTimer = setInterval(() => this.send({ t: 'ping', sent: performance.now() }), 2000);
  }

  private stopPing(): void {
    if (this.pingTimer) clearInterval(this.pingTimer);
    this.pingTimer = null;
  }

  send(msg: C2S): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) return;
    this.socket.send(encodeJson(msg));
  }

  sendInputs(inputs: Input[], lastAckedTick: number): void {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN || inputs.length === 0) return;
    this.socket.send(encodeInput({ inputs, lastAckedTick }));
  }

  onMessage(handler: MessageHandler): () => void {
    this.messageHandlers.add(handler);
    return () => this.messageHandlers.delete(handler);
  }

  onSnapshot(handler: SnapshotHandler): () => void {
    this.snapshotHandlers.add(handler);
    return () => this.snapshotHandlers.delete(handler);
  }

  onStateChange(handler: StateHandler): () => void {
    this.stateHandlers.add(handler);
    return () => this.stateHandlers.delete(handler);
  }

  private setState(state: ConnectionState): void {
    if (this.state === state) return;
    this.state = state;
    for (const handler of this.stateHandlers) handler(state);
  }

  close(): void {
    this.stopPing();
    if (this.socket) {
      this.socket.onclose = null;
      this.socket.close();
      this.socket = null;
    }
    this.setState('idle');
  }
}
