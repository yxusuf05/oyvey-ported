// Virtual per-project file store backed by SQLite. The AI only ever works on
// these rows; a build assembles them into a real directory. The allowlist is
// a security boundary: nothing outside src/ and assets/ (plus pack.png/
// pack.mcmeta for resource packs) can be written, so the model can never
// modify Gradle scripts or the wrapper.
import { db } from '../db.js';

const ALLOWED = /^(src\/main\/(java|resources)\/|assets\/)/;
const ALLOWED_EXACT = new Set(['pack.png', 'pack.mcmeta']);

export function normalizePath(raw) {
  const p = String(raw ?? '').replace(/\\/g, '/').replace(/^\.?\//, '');
  if (!p || p.length > 300 || p.includes('..') || p.includes('\0') || /[^ -~]/.test(p)) return null;
  if (!ALLOWED.test(p) && !ALLOWED_EXACT.has(p)) return null;
  return p;
}

export function listFiles(projectId) {
  return db.prepare('SELECT path, length(content) AS size, is_binary FROM project_files WHERE project_id=? ORDER BY path').all(projectId);
}

export function readFile(projectId, path) {
  const row = db.prepare('SELECT content, is_binary FROM project_files WHERE project_id=? AND path=?').get(projectId, path);
  if (!row) return null;
  return { content: row.is_binary ? row.content : row.content.toString('utf8'), isBinary: Boolean(row.is_binary) };
}

export function writeFile(projectId, path, content, { isBinary = false } = {}) {
  const buf = Buffer.isBuffer(content) ? content : Buffer.from(content, 'utf8');
  db.prepare(`
    INSERT INTO project_files (project_id, path, content, is_binary, updated_at)
    VALUES (?, ?, ?, ?, datetime('now'))
    ON CONFLICT(project_id, path) DO UPDATE SET content=excluded.content, is_binary=excluded.is_binary, updated_at=excluded.updated_at
  `).run(projectId, path, buf, isBinary ? 1 : 0);
}

export function deleteFile(projectId, path) {
  return db.prepare('DELETE FROM project_files WHERE project_id=? AND path=?').run(projectId, path).changes > 0;
}

export function fileCount(projectId) {
  return db.prepare('SELECT count(*) AS n FROM project_files WHERE project_id=?').get(projectId).n;
}

export function filesAsMap(projectId) {
  const map = new Map();
  for (const row of db.prepare('SELECT path, content, is_binary FROM project_files WHERE project_id=?').all(projectId)) {
    map.set(row.path, row.is_binary ? row.content : row.content.toString('utf8'));
  }
  return map;
}
