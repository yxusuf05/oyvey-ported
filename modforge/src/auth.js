import crypto from 'node:crypto';
import { db } from '../db.js';

const SESSION_DAYS = 30;

function hashPassword(password, salt = crypto.randomBytes(16)) {
  const hash = crypto.scryptSync(password, salt, 64);
  return `${salt.toString('hex')}:${hash.toString('hex')}`;
}

function verifyPassword(password, stored) {
  const [saltHex, hashHex] = String(stored).split(':');
  const salt = Buffer.from(saltHex, 'hex');
  const expected = Buffer.from(hashHex, 'hex');
  const actual = crypto.scryptSync(password, salt, 64);
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
}

export function signup(email, password) {
  email = String(email ?? '').trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw httpError(400, 'Ungültige E-Mail-Adresse.');
  if (String(password ?? '').length < 8) throw httpError(400, 'Passwort braucht mindestens 8 Zeichen.');
  try {
    const info = db.prepare('INSERT INTO users (email, pass_hash) VALUES (?,?)').run(email, hashPassword(password));
    return createSession(Number(info.lastInsertRowid));
  } catch (e) {
    if (String(e.message).includes('UNIQUE')) throw httpError(409, 'Diese E-Mail ist schon registriert.');
    throw e;
  }
}

export function login(email, password) {
  const user = db.prepare('SELECT * FROM users WHERE email=?').get(String(email ?? '').trim().toLowerCase());
  if (!user || !verifyPassword(String(password ?? ''), user.pass_hash)) {
    throw httpError(401, 'E-Mail oder Passwort falsch.');
  }
  return createSession(user.id);
}

export function logout(token) {
  if (token) db.prepare('DELETE FROM sessions WHERE token=?').run(token);
}

function createSession(userId) {
  const token = crypto.randomBytes(32).toString('hex');
  const expires = new Date(Date.now() + SESSION_DAYS * 86400_000).toISOString();
  db.prepare('INSERT INTO sessions (token, user_id, expires_at) VALUES (?,?,?)').run(token, userId, expires);
  return { token, userId };
}

export function sessionCookie(token) {
  return `mf_session=${token}; HttpOnly; Path=/; Max-Age=${SESSION_DAYS * 86400}; SameSite=Lax`;
}

export function clearSessionCookie() {
  return 'mf_session=; HttpOnly; Path=/; Max-Age=0; SameSite=Lax';
}

export function userFromRequest(req) {
  const token = (req.headers.cookie ?? '').split(';').map((s) => s.trim()).find((s) => s.startsWith('mf_session='))?.slice('mf_session='.length);
  if (!token) return null;
  const row = db.prepare(`
    SELECT u.* , s.token AS session_token FROM sessions s JOIN users u ON u.id = s.user_id
    WHERE s.token = ? AND s.expires_at > datetime('now')
  `).get(token);
  return row ?? null;
}

// Express middleware: attach req.user or reject.
export function requireAuth(req, res, next) {
  const user = userFromRequest(req);
  if (!user) return res.status(401).json({ error: 'Nicht eingeloggt.' });
  req.user = user;
  next();
}

export function httpError(status, message) {
  return Object.assign(new Error(message), { status });
}

// Basic in-memory rate limiter for the auth endpoints (brute-force brake).
const attempts = new Map();
export function authRateLimit(req, res, next) {
  const key = req.ip;
  const now = Date.now();
  const entry = attempts.get(key) ?? { count: 0, reset: now + 15 * 60_000 };
  if (now > entry.reset) { entry.count = 0; entry.reset = now + 15 * 60_000; }
  if (++entry.count > 30) return res.status(429).json({ error: 'Zu viele Versuche — warte ein paar Minuten.' });
  attempts.set(key, entry);
  next();
}
