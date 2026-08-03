/**
 * Meta-progression storage.
 *
 * `node:sqlite` ships with Node 22, so this adds persistence without adding a dependency —
 * which is the whole reason it was chosen. It prints an ExperimentalWarning on import; that
 * is accepted, because the alternative is a native module in a project that deliberately
 * has none.
 *
 * Everything here is synchronous. Writes happen once at the end of a run and once per shop
 * purchase, never in the 60 Hz loop, so there is nothing to gain from an async wrapper and
 * a great deal of complexity to lose.
 */

// Aliased: the esbuild server bundle prepends its own `createRequire` import, and two
// declarations of the same name in one module scope is a syntax error at load time.
import { createRequire as nodeCreateRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { perkCost, type PerkLevels } from '@game/shared/content';

/**
 * Loaded through `createRequire` rather than imported.
 *
 * Vite's bundled resolver does not recognise `node:sqlite` yet — it strips the prefix, goes
 * looking for a file called `sqlite`, and fails. A runtime require is never analysed by the
 * bundler and resolves the builtin directly, which keeps both the test runner and the
 * esbuild server bundle working without either needing to be taught about it.
 */
const { DatabaseSync } = nodeCreateRequire(import.meta.url)('node:sqlite') as typeof import('node:sqlite');
type DatabaseSync = InstanceType<typeof DatabaseSync>;

export interface Profile {
  id: string;
  credits: number;
  runs: number;
  /** Deepest descent ever reached, 0..1. Bragging rights, and a hook for later levels. */
  deepest: number;
  perks: PerkLevels;
}

export type PurchaseResult =
  | { ok: true; profile: Profile }
  | { ok: false; reason: 'unknown_perk' | 'maxed' | 'too_expensive' };

/** Ids are client-generated; sanitising them keeps anything odd out of the table. */
function cleanId(id: string): string {
  return id.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 64);
}

export class ProgressionStore {
  private readonly db: DatabaseSync;

  /**
   * `PRISMA_MEMORY_DB=1` opens an in-memory database. Tests and the Playwright web server
   * set it, so no test run writes to disk or depends on what the previous one left behind.
   */
  constructor(directory = process.env.DATA_DIR ?? './data') {
    const memory = process.env.PRISMA_MEMORY_DB === '1';
    if (!memory) {
      mkdirSync(directory, { recursive: true });
    }
    this.db = new DatabaseSync(memory ? ':memory:' : join(directory, 'prisma.sqlite'));
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS profiles (
        id TEXT PRIMARY KEY,
        credits INTEGER NOT NULL DEFAULT 0,
        runs INTEGER NOT NULL DEFAULT 0,
        deepest REAL NOT NULL DEFAULT 0,
        updated INTEGER NOT NULL DEFAULT 0
      );
      CREATE TABLE IF NOT EXISTS perks (
        profile_id TEXT NOT NULL,
        perk TEXT NOT NULL,
        level INTEGER NOT NULL,
        PRIMARY KEY (profile_id, perk)
      );
    `);
  }

  /** Reads a profile, creating an empty one on first sight. */
  load(rawId: string): Profile {
    const id = cleanId(rawId);
    if (id.length === 0) return { id: '', credits: 0, runs: 0, deepest: 0, perks: {} };

    this.db.prepare('INSERT OR IGNORE INTO profiles (id) VALUES (?)').run(id);
    const row = this.db.prepare('SELECT credits, runs, deepest FROM profiles WHERE id = ?').get(id) as
      | { credits: number; runs: number; deepest: number }
      | undefined;

    const perks: PerkLevels = {};
    const rows = this.db.prepare('SELECT perk, level FROM perks WHERE profile_id = ?').all(id) as {
      perk: string;
      level: number;
    }[];
    for (const entry of rows) perks[entry.perk] = entry.level;

    return {
      id,
      credits: row?.credits ?? 0,
      runs: row?.runs ?? 0,
      deepest: row?.deepest ?? 0,
      perks,
    };
  }

  /** Banks the takings from one finished run. */
  award(rawId: string, credits: number, deepest: number): Profile {
    const id = cleanId(rawId);
    if (id.length === 0) return this.load(rawId);
    this.load(id);
    this.db
      .prepare(
        `UPDATE profiles
         SET credits = credits + ?, runs = runs + 1, deepest = MAX(deepest, ?), updated = ?
         WHERE id = ?`,
      )
      .run(Math.max(0, Math.round(credits)), deepest, Date.now(), id);
    return this.load(id);
  }

  /**
   * Buys the next level of a perk.
   *
   * Every check is here rather than in the caller: the client sends a wish, and a wish that
   * cannot be afforded has to cost nothing at all — not the credits, not the level.
   */
  buyPerk(rawId: string, perk: string): PurchaseResult {
    const profile = this.load(rawId);
    if (profile.id.length === 0) return { ok: false, reason: 'unknown_perk' };

    const level = profile.perks[perk] ?? 0;
    const cost = perkCost(perk, level);
    if (cost === null) {
      return { ok: false, reason: perkCost(perk, 0) === null ? 'unknown_perk' : 'maxed' };
    }
    if (profile.credits < cost) return { ok: false, reason: 'too_expensive' };

    this.db.prepare('UPDATE profiles SET credits = credits - ?, updated = ? WHERE id = ?').run(
      cost,
      Date.now(),
      profile.id,
    );
    this.db
      .prepare(
        `INSERT INTO perks (profile_id, perk, level) VALUES (?, ?, 1)
         ON CONFLICT(profile_id, perk) DO UPDATE SET level = level + 1`,
      )
      .run(profile.id, perk);

    return { ok: true, profile: this.load(profile.id) };
  }

  close(): void {
    this.db.close();
  }
}
