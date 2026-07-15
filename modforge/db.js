import Database from 'better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';
import { config } from './config.js';

fs.mkdirSync(config.dataDir, { recursive: true });
export const db = new Database(path.join(config.dataDir, 'modforge.db'));
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT UNIQUE NOT NULL,
  pass_hash TEXT NOT NULL,
  plan TEXT NOT NULL DEFAULT 'free',
  stripe_customer_id TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id),
  expires_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS projects (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  kind TEXT NOT NULL DEFAULT 'mod' CHECK(kind IN ('mod','resourcepack')),
  mod_id TEXT NOT NULL,
  template_id TEXT NOT NULL DEFAULT 'fabric-1.21.11',
  status TEXT NOT NULL DEFAULT 'idle' CHECK(status IN ('idle','running','waiting_user')),
  pending_tool_id TEXT,
  pending_results TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL REFERENCES projects(id),
  role TEXT NOT NULL CHECK(role IN ('user','assistant')),
  content_json TEXT NOT NULL,
  display_text TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_messages_project ON messages(project_id, id);
CREATE TABLE IF NOT EXISTS project_files (
  project_id INTEGER NOT NULL REFERENCES projects(id),
  path TEXT NOT NULL,
  content BLOB NOT NULL,
  is_binary INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (project_id, path)
);
CREATE TABLE IF NOT EXISTS jobs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL REFERENCES projects(id),
  type TEXT NOT NULL CHECK(type IN ('build','pack')),
  status TEXT NOT NULL DEFAULT 'queued' CHECK(status IN ('queued','running','success','failed')),
  log TEXT,
  artifact_id INTEGER,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  finished_at TEXT
);
CREATE TABLE IF NOT EXISTS artifacts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id INTEGER NOT NULL REFERENCES projects(id),
  filename TEXT NOT NULL,
  disk_path TEXT NOT NULL,
  size INTEGER NOT NULL,
  sha256 TEXT NOT NULL,
  delivered INTEGER NOT NULL DEFAULT 0,
  summary TEXT,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE IF NOT EXISTS usage (
  user_id INTEGER NOT NULL REFERENCES users(id),
  day TEXT NOT NULL,
  messages_used INTEGER NOT NULL DEFAULT 0,
  builds_used INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day)
);
`);

// Recover from a crash/restart: anything mid-flight is failed/idle now.
db.prepare(`UPDATE jobs SET status='failed', log=coalesce(log,'') || char(10) || 'aborted: server restart', finished_at=datetime('now') WHERE status IN ('queued','running')`).run();
db.prepare(`UPDATE projects SET status='idle' WHERE status='running'`).run();

export function today() {
  return new Date().toISOString().slice(0, 10);
}

// Atomically consume one unit of daily usage; returns false when the plan
// limit is already reached (nothing is consumed in that case).
export function consumeUsage(userId, column, limit) {
  if (column !== 'messages_used' && column !== 'builds_used') throw new Error('bad usage column');
  const row = db.prepare(`
    INSERT INTO usage (user_id, day, ${column}) VALUES (?, ?, 1)
    ON CONFLICT(user_id, day) DO UPDATE SET ${column} = ${column} + 1 WHERE ${column} < ?
    RETURNING ${column}
  `).get(userId, today(), limit);
  return row !== undefined;
}

export function usageToday(userId) {
  return db.prepare('SELECT messages_used, builds_used FROM usage WHERE user_id=? AND day=?').get(userId, today())
    ?? { messages_used: 0, builds_used: 0 };
}
