/**
 * Room registry, room codes and reconnection.
 *
 * Session tokens outlive the socket: a dropped connection leaves the player's body in the
 * world, and reconnecting with the same token resumes it. Losing a fifteen-minute run to
 * a wifi hiccup is the single most annoying failure mode in this genre.
 */

import { randomBytes } from 'node:crypto';
import type { WebSocket } from 'ws';
import { isValidRoomCode, randomRoomCode } from '@game/shared/prng';
import { MAX_PLAYERS } from '@game/shared/sim';
import { GameRoom, ROOM_IDLE_TTL, sanitiseName, type RoomPlayer } from './room';

export interface Session {
  roomCode: string;
  playerId: number;
}

export type JoinResult =
  | { ok: true; room: GameRoom; player: RoomPlayer; reconnected: boolean }
  | { ok: false; code: string; message: string };

export class RoomManager {
  private readonly rooms = new Map<string, GameRoom>();
  private readonly sessions = new Map<string, Session>();
  private nextPlayerId = 1;

  get roomCount(): number {
    return this.rooms.size;
  }

  get playerCount(): number {
    let total = 0;
    for (const room of this.rooms.values()) total += room.connectedCount;
    return total;
  }

  createRoom(): GameRoom {
    let code = randomRoomCode();
    // Rejection-sample against live rooms; six characters from a 32-symbol alphabet make
    // collisions rare, but "rare" is not "never" and a collision would be baffling.
    for (let attempt = 0; attempt < 64 && this.rooms.has(code); attempt++) code = randomRoomCode();
    const room = new GameRoom(code);
    this.rooms.set(code, room);
    return room;
  }

  getRoom(code: string): GameRoom | undefined {
    return this.rooms.get(code.toUpperCase());
  }

  /** Joins an existing room, resuming a previous session when the token still matches. */
  join(code: string, name: string, socket: WebSocket, sessionToken?: string): JoinResult {
    const normalised = code.toUpperCase();
    if (!isValidRoomCode(normalised)) {
      return { ok: false, code: 'bad_code', message: 'That room code is not valid.' };
    }
    const room = this.rooms.get(normalised);
    if (!room) return { ok: false, code: 'no_room', message: 'No room with that code.' };

    if (sessionToken) {
      const session = this.sessions.get(sessionToken);
      if (session && session.roomCode === normalised) {
        const existing = room.findPlayer(session.playerId);
        if (existing && existing.socket === null) {
          existing.name = sanitiseName(name);
          existing.socket = socket;
          // `welcome` must land before any `roomState`: the client cannot tell which entry
          // in the player list is itself until it knows its own id, and a lobby rendered
          // before that shows the host "waiting for the host".
          room.welcome(existing);
          room.setConnected(existing, socket);
          return { ok: true, room, player: existing, reconnected: true };
        }
      }
    }

    if (room.isFull) return { ok: false, code: 'room_full', message: `Room is full (${MAX_PLAYERS} players).` };

    const player = this.makePlayer(name, socket);
    this.sessions.set(player.sessionToken, { roomCode: normalised, playerId: player.id });
    room.welcome(player);
    room.addPlayer(player);
    return { ok: true, room, player, reconnected: false };
  }

  host(name: string, socket: WebSocket): JoinResult {
    const room = this.createRoom();
    const player = this.makePlayer(name, socket);
    this.sessions.set(player.sessionToken, { roomCode: room.code, playerId: player.id });
    room.welcome(player);
    room.addPlayer(player);
    return { ok: true, room, player, reconnected: false };
  }

  private makePlayer(name: string, socket: WebSocket): RoomPlayer {
    return {
      id: this.nextPlayerId++,
      name: sanitiseName(name),
      ready: false,
      sessionToken: randomBytes(16).toString('hex'),
      socket,
      sim: null,
    };
  }

  leave(room: GameRoom, player: RoomPlayer): void {
    room.removePlayer(player.id);
    this.sessions.delete(player.sessionToken);
    if (room.isEmpty) this.rooms.delete(room.code);
  }

  update(dt: number): void {
    for (const [code, room] of this.rooms) {
      room.update(dt);
      if (room.isEmpty || room.idleFor > ROOM_IDLE_TTL) {
        for (const player of room.players) this.sessions.delete(player.sessionToken);
        this.rooms.delete(code);
      }
    }
  }
}
