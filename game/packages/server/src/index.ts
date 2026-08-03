/**
 * Server entry point: one HTTP port serving both the built client and the WebSocket.
 *
 * One port means one container, one URL and one thing to deploy — the friend you want to
 * play with opens a link and types a room code, and nothing about ports or NAT ever comes
 * up.
 */

import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { networkInterfaces } from 'node:os';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WebSocketServer, type WebSocket } from 'ws';
import { TICK_RATE } from '@game/shared/sim';
import {
  MessageTag,
  PROTOCOL_VERSION,
  decodeInput,
  decodeJson,
  encodeJson,
  messageTagOf,
  type C2S,
  type S2C,
} from '@game/shared/protocol';
import { RoomManager } from './rooms/manager';
import type { GameRoom, RoomPlayer } from './rooms/room';

const PORT = Number(process.env.PORT ?? 8787);
const HOST = process.env.HOST ?? '0.0.0.0';
const here = fileURLToPath(new URL('.', import.meta.url));
const CLIENT_DIST = process.env.CLIENT_DIST ?? resolve(here, '../../client/dist');

const manager = new RoomManager();

// ---------------------------------------------------------------------------
// Static file serving
// ---------------------------------------------------------------------------

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.wasm': 'application/wasm',
};

function serveStatic(req: IncomingMessage, res: ServerResponse): void {
  const url = new URL(req.url ?? '/', 'http://localhost');
  let pathname = decodeURIComponent(url.pathname);
  if (pathname === '/') pathname = '/index.html';

  // Reject traversal before touching the filesystem.
  const safe = normalize(pathname).replace(/^(\.\.[/\\])+/, '');
  let filePath = join(CLIENT_DIST, safe);
  if (!filePath.startsWith(CLIENT_DIST)) {
    res.writeHead(403).end('Forbidden');
    return;
  }

  if (!existsSync(filePath) || !statSync(filePath).isFile()) {
    // Single-page app: unknown paths fall back to the shell.
    filePath = join(CLIENT_DIST, 'index.html');
    if (!existsSync(filePath)) {
      res.writeHead(404, { 'content-type': 'text/plain' });
      res.end('Client build not found. Run `pnpm build` first, or use `pnpm dev`.');
      return;
    }
  }

  const type = MIME[extname(filePath)] ?? 'application/octet-stream';
  const immutable = filePath.includes('/assets/');
  res.writeHead(200, {
    'content-type': type,
    'cache-control': immutable ? 'public, max-age=31536000, immutable' : 'no-cache',
  });
  createReadStream(filePath).pipe(res);
}

const httpServer = createServer((req, res) => {
  if (req.url === '/healthz') {
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ ok: true, rooms: manager.roomCount, players: manager.playerCount }));
    return;
  }
  serveStatic(req, res);
});

// ---------------------------------------------------------------------------
// WebSocket
// ---------------------------------------------------------------------------

interface Connection {
  socket: WebSocket;
  room: GameRoom | null;
  player: RoomPlayer | null;
  name: string;
  helloed: boolean;
}

const wss = new WebSocketServer({ noServer: true });
const connections = new Map<WebSocket, Connection>();

httpServer.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url ?? '/', 'http://localhost');
  if (url.pathname !== '/ws') {
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => wss.emit('connection', ws, req));
});

wss.on('connection', (socket: WebSocket) => {
  const conn: Connection = { socket, room: null, player: null, name: 'Anon', helloed: false };
  connections.set(socket, conn);

  socket.on('message', (data: Buffer, isBinary: boolean) => {
    try {
      if (isBinary) handleBinary(conn, data);
      else handleJson(conn, data.toString('utf8'));
    } catch (error) {
      console.error('[ws] message failed:', error);
      sendRaw(socket, { t: 'error', code: 'bad_message', message: 'Malformed message.' });
    }
  });

  socket.on('close', () => {
    connections.delete(socket);
    if (conn.room && conn.player) {
      // The body stays in the world; the manager decides when the grace period is up.
      conn.room.setConnected(conn.player, null);
    }
  });

  socket.on('error', () => socket.terminate());
});

function handleBinary(conn: Connection, data: Buffer): void {
  const buffer = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) as ArrayBuffer;
  if (messageTagOf(buffer) !== MessageTag.Input) return;
  if (!conn.room || !conn.player) return;
  const payload = decodeInput(buffer);
  conn.room.handleInput(conn.player, payload.inputs, payload.lastAckedTick);
}

function handleJson(conn: Connection, text: string): void {
  const msg = decodeJson<C2S>(text);

  if (msg.t === 'hello') {
    if (msg.protocol !== PROTOCOL_VERSION) {
      sendRaw(conn.socket, {
        t: 'error',
        code: 'protocol_mismatch',
        message: `Client protocol ${msg.protocol} does not match server ${PROTOCOL_VERSION}. Reload the page.`,
      });
      conn.socket.close();
      return;
    }
    conn.name = msg.name;
    conn.helloed = true;
    return;
  }

  if (!conn.helloed) {
    sendRaw(conn.socket, { t: 'error', code: 'no_hello', message: 'Send hello first.' });
    return;
  }

  if (msg.t === 'createRoom') {
    leaveCurrentRoom(conn);
    const result = manager.host(conn.name, conn.socket);
    if (result.ok) {
      conn.room = result.room;
      conn.player = result.player;
    }
    return;
  }

  if (msg.t === 'joinRoom') {
    leaveCurrentRoom(conn);
    const result = manager.join(msg.code, conn.name, conn.socket, undefined);
    if (!result.ok) {
      sendRaw(conn.socket, { t: 'error', code: result.code, message: result.message });
      return;
    }
    conn.room = result.room;
    conn.player = result.player;
    return;
  }

  if (msg.t === 'leaveRoom') {
    leaveCurrentRoom(conn);
    return;
  }

  if (!conn.room || !conn.player) {
    sendRaw(conn.socket, { t: 'error', code: 'no_room', message: 'You are not in a room.' });
    return;
  }
  conn.room.handleMessage(conn.player, msg);
}

function leaveCurrentRoom(conn: Connection): void {
  if (conn.room && conn.player) manager.leave(conn.room, conn.player);
  conn.room = null;
  conn.player = null;
}

function sendRaw(socket: WebSocket, msg: S2C): void {
  if (socket.readyState === 1) socket.send(encodeJson(msg));
}

// ---------------------------------------------------------------------------
// Fixed-step loop
// ---------------------------------------------------------------------------

const STEP_MS = 1000 / TICK_RATE;
let previous = Date.now();
let slowTicks = 0;

setInterval(() => {
  const now = Date.now();
  const dt = Math.min(0.25, (now - previous) / 1000);
  previous = now;

  const started = process.hrtime.bigint();
  manager.update(dt);
  const elapsedMs = Number(process.hrtime.bigint() - started) / 1e6;

  // A tick that eats most of its budget is the early warning for everything getting worse
  // at once; log it rather than discovering it as unexplained lag.
  if (elapsedMs > 8) {
    slowTicks++;
    if (slowTicks % 60 === 1) {
      console.warn(`[sim] slow tick ${elapsedMs.toFixed(1)}ms (rooms=${manager.roomCount})`);
    }
  }
}, STEP_MS);

/**
 * The address a friend on the same network should type.
 *
 * `HOST` is `0.0.0.0`, which is correct for binding and useless to a human — so the banner
 * resolves the first non-internal IPv4 address instead. Without this, "how does my friend
 * join?" has no answer anywhere in the output.
 */
function lanAddress(): string | null {
  for (const addresses of Object.values(networkInterfaces())) {
    for (const address of addresses ?? []) {
      if (address.family === 'IPv4' && !address.internal) return address.address;
    }
  }
  return null;
}

httpServer.on('error', (error: NodeJS.ErrnoException) => {
  if (error.code === 'EADDRINUSE') {
    console.error(`\nPort ${PORT} is already in use — PRISMA is probably already running.`);
    console.error(`Port ${PORT} ist belegt — PRISMA läuft vermutlich schon.\n`);
    process.exit(1);
  }
  throw error;
});

httpServer.listen(PORT, HOST, () => {
  const lan = lanAddress();
  console.log('');
  console.log('  PRISMA läuft. / PRISMA is running.');
  console.log('');
  console.log(`  Du:            http://localhost:${PORT}`);
  if (lan) console.log(`  Dein Freund:   http://${lan}:${PORT}   (gleiches WLAN / same network)`);
  console.log('');
  console.log('  Beenden mit Strg+C. / Stop with Ctrl+C.');
  console.log('');
  if (!existsSync(CLIENT_DIST)) {
    console.warn(`  Warning: no client build at ${CLIENT_DIST} — run \`pnpm run build\`.`);
  }
});

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.on(signal, () => {
    console.log(`\n${signal} received, shutting down.`);
    for (const socket of connections.keys()) socket.close();
    httpServer.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 2000).unref();
  });
}
