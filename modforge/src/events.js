// Per-project event bus bridging the agent loop to SSE subscribers.
import { EventEmitter } from 'node:events';

const buses = new Map();

export function bus(projectId) {
  let b = buses.get(projectId);
  if (!b) {
    b = new EventEmitter();
    b.setMaxListeners(50);
    buses.set(projectId, b);
  }
  return b;
}

export function emit(projectId, type, data = {}) {
  bus(projectId).emit('event', { type, ...data });
}
