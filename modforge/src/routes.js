import express from 'express';
import fs from 'node:fs';
import { config, resolveModel } from '../config.js';
import { db, usageToday, consumeUsage } from '../db.js';
import { signup, login, logout, sessionCookie, clearSessionCookie, requireAuth, authRateLimit, userFromRequest } from './auth.js';
import { submitUserMessage } from './agent/loop.js';
import { bus } from './events.js';
import { sanitizeModId } from './build/assemble.js';
import * as ws from './workspace.js';

export const api = express.Router();

// ---------- auth ----------
api.post('/auth/signup', authRateLimit, (req, res) => {
  const { token } = signup(req.body?.email, req.body?.password);
  res.setHeader('Set-Cookie', sessionCookie(token));
  res.json({ ok: true });
});

api.post('/auth/login', authRateLimit, (req, res) => {
  const { token } = login(req.body?.email, req.body?.password);
  res.setHeader('Set-Cookie', sessionCookie(token));
  res.json({ ok: true });
});

api.post('/auth/logout', (req, res) => {
  const user = userFromRequest(req);
  logout(user?.session_token);
  res.setHeader('Set-Cookie', clearSessionCookie());
  res.json({ ok: true });
});

api.get('/me', requireAuth, (req, res) => {
  const planCfg = config.plans[req.user.plan] || config.plans.free;
  let backend = null;
  try { backend = resolveModel(req.user.plan); } catch { /* unconfigured */ }
  res.json({
    email: req.user.email,
    plan: req.user.plan,
    usage: usageToday(req.user.id),
    limits: {
      messagesPerDay: planCfg.messagesPerDay,
      buildsPerDay: planCfg.buildsPerDay,
      maxFiles: planCfg.maxFiles,
      texturesInMods: planCfg.texturesInMods,
    },
    backend,
    devUpgrade: config.allowDevUpgrade,
  });
});

// ---------- projects ----------
api.get('/projects', requireAuth, (req, res) => {
  res.json(db.prepare('SELECT id, name, kind, mod_id, status, created_at FROM projects WHERE user_id=? ORDER BY id DESC').all(req.user.id));
});

api.post('/projects', requireAuth, (req, res) => {
  const name = String(req.body?.name ?? '').trim().slice(0, 60);
  const kind = req.body?.kind === 'resourcepack' ? 'resourcepack' : 'mod';
  if (!name) return res.status(400).json({ error: 'Projektname fehlt.' });
  const modId = sanitizeModId(req.body?.mod_id || name);
  const info = db.prepare('INSERT INTO projects (user_id, name, kind, mod_id, template_id) VALUES (?,?,?,?,?)')
    .run(req.user.id, name, kind, modId, config.defaultTemplate);
  res.json(db.prepare('SELECT id, name, kind, mod_id, status FROM projects WHERE id=?').get(info.lastInsertRowid));
});

function ownedProject(req, res) {
  const project = db.prepare('SELECT * FROM projects WHERE id=? AND user_id=?').get(req.params.id, req.user.id);
  if (!project) { res.status(404).json({ error: 'Projekt nicht gefunden.' }); return null; }
  return project;
}

api.get('/projects/:id', requireAuth, (req, res) => {
  const project = ownedProject(req, res);
  if (!project) return;
  const messages = db.prepare('SELECT role, content_json, display_text, created_at FROM messages WHERE project_id=? ORDER BY id').all(project.id)
    .map((m) => {
      if (m.role === 'user') return m.display_text ? { role: 'user', text: m.display_text, at: m.created_at } : null;
      const blocks = JSON.parse(m.content_json);
      const text = blocks.filter((b) => b.type === 'text').map((b) => b.text).join('\n').trim();
      return text ? { role: 'assistant', text, at: m.created_at } : null;
    })
    .filter(Boolean);
  const artifacts = db.prepare('SELECT id, filename, size, summary, created_at FROM artifacts WHERE project_id=? AND delivered=1 ORDER BY id').all(project.id);
  const pending = project.status === 'waiting_user' && project.pending_tool_id
    ? lastQuestion(project.id)
    : null;
  res.json({
    id: project.id, name: project.name, kind: project.kind, mod_id: project.mod_id,
    status: project.status, messages, artifacts,
    files: ws.listFiles(project.id),
    question: pending,
  });
});

// Recover the pending ask_user question from the last assistant message (for page reloads).
function lastQuestion(projectId) {
  const row = db.prepare("SELECT content_json FROM messages WHERE project_id=? AND role='assistant' ORDER BY id DESC LIMIT 1").get(projectId);
  if (!row) return null;
  const tu = JSON.parse(row.content_json).find((b) => b.type === 'tool_use' && b.name === 'ask_user');
  return tu ? { question: String(tu.input?.question ?? ''), options: tu.input?.options ?? [] } : null;
}

api.post('/projects/:id/messages', requireAuth, (req, res) => {
  const project = ownedProject(req, res);
  if (!project) return;
  const text = String(req.body?.text ?? '').trim();
  if (!text) return res.status(400).json({ error: 'Leere Nachricht.' });
  if (text.length > 4000) return res.status(400).json({ error: 'Nachricht zu lang (max. 4000 Zeichen).' });
  if (project.status === 'running') return res.status(409).json({ error: 'Die KI arbeitet gerade — warte, bis sie fertig ist.' });

  const planCfg = config.plans[req.user.plan] || config.plans.free;
  try { resolveModel(req.user.plan); } catch (e) { return res.status(e.status ?? 503).json({ error: e.message }); }
  if (!consumeUsage(req.user.id, 'messages_used', planCfg.messagesPerDay)) {
    return res.status(429).json({
      error: `Tageslimit erreicht (${planCfg.messagesPerDay} Nachrichten/Tag im ${planCfg.label}-Plan).`,
      upgrade: req.user.plan === 'free',
    });
  }
  submitUserMessage(project, req.user, text);
  res.json({ ok: true });
});

// ---------- SSE ----------
api.get('/projects/:id/events', requireAuth, (req, res) => {
  const project = ownedProject(req, res);
  if (!project) return;
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  res.write(`data: ${JSON.stringify({ type: 'status', status: project.status })}\n\n`);
  const b = bus(project.id);
  const onEvent = (ev) => res.write(`data: ${JSON.stringify(ev)}\n\n`);
  b.on('event', onEvent);
  const ping = setInterval(() => res.write(': ping\n\n'), 25000);
  req.on('close', () => { b.off('event', onEvent); clearInterval(ping); });
});

// ---------- artifacts ----------
api.get('/artifacts/:id/download', requireAuth, (req, res) => {
  const artifact = db.prepare(`
    SELECT a.* FROM artifacts a JOIN projects p ON p.id = a.project_id
    WHERE a.id=? AND p.user_id=?
  `).get(req.params.id, req.user.id);
  if (!artifact || !fs.existsSync(artifact.disk_path)) return res.status(404).json({ error: 'Datei nicht gefunden.' });
  res.setHeader('Content-Disposition', `attachment; filename="${artifact.filename.replace(/[^\w.-]/g, '_')}"`);
  res.setHeader('Content-Type', 'application/octet-stream');
  fs.createReadStream(artifact.disk_path).pipe(res);
});
