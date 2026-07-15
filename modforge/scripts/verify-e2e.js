#!/usr/bin/env node
// Deterministic self-tests: workspace assembly, error parser, texture
// renderer, resource-pack zipper, and the build pipeline (against a fake
// Gradle so it runs without network/Fabric access). Set VERIFY_REAL_BUILD=1
// on a machine with Fabric/Gradle network access to run the fixture mod
// through the real toolchain instead.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.dirname(here);
const realBuild = process.env.VERIFY_REAL_BUILD === '1';

// Isolate all state before importing config/db.
process.env.DATA_DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'modforge-verify-'));
if (!realBuild) {
  process.env.MODFORGE_GRADLE = path.join(here, 'fixtures', 'fake-gradle.sh');
  fs.chmodSync(process.env.MODFORGE_GRADLE, 0o755);
  fs.chmodSync(path.join(here, 'fixtures', 'fake-gradle-fail.sh'), 0o755);
}

const { config } = await import('../config.js');
const { db } = await import('../db.js');
const { assembleWorkspace, sanitizeModId, pkgIdFrom } = await import('../src/build/assemble.js');
const { parseBuildErrors } = await import('../src/build/errors.js');
const { renderTexture } = await import('../src/textures/renderer.js');
const { packResourcePack } = await import('../src/textures/packer.js');
const { runBuild } = await import('../src/build/runner.js');
const ws = await import('../src/workspace.js');

let failures = 0;
function check(name, cond, detail = '') {
  if (cond) console.log(`  ✔ ${name}`);
  else { console.error(`  ✘ ${name} ${detail}`); failures++; }
}

// ---------- 1. workspace assembly ----------
console.log('\n[1] template assembly');
{
  check('sanitizeModId', sanitizeModId('Ruby Mod!') === 'ruby_mod', sanitizeModId('Ruby Mod!'));
  check('pkgIdFrom strips hyphens', pkgIdFrom('my-mod') === 'mymod', pkgIdFrom('my-mod'));

  const dest = path.join(config.dataDir, 'assembly-test');
  const files = new Map([
    ['src/main/java/com/modforge/__PKG_ID__/ModItems.java', fs.readFileSync(path.join(here, 'fixtures', 'ruby-mod', 'ModItems.java'), 'utf8')],
  ]);
  assembleWorkspace({
    templateDir: path.join(config.templatesDir, 'fabric-1.21.11'),
    destDir: dest,
    vars: { modId: 'Ruby Mod!', modName: 'Ruby "Mod"', modVersion: '1.0.0', modDescription: 'test' },
    files,
  });
  const fmj = JSON.parse(fs.readFileSync(path.join(dest, 'src/main/resources/fabric.mod.json'), 'utf8'));
  check('fabric.mod.json id substituted', fmj.id === 'ruby_mod', fmj.id);
  check('fabric.mod.json valid despite quotes in name', fmj.name.includes('Ruby'), fmj.name);
  check('entrypoint package substituted', fmj.entrypoints.main[0] === 'com.modforge.ruby_mod.ModEntry', fmj.entrypoints.main[0]);
  check('package dir renamed', fs.existsSync(path.join(dest, 'src/main/java/com/modforge/ruby_mod/ModEntry.java')));
  check('mixins file renamed', fs.existsSync(path.join(dest, 'src/main/resources/ruby_mod.mixins.json')));
  const overlay = fs.readFileSync(path.join(dest, 'src/main/java/com/modforge/ruby_mod/ModItems.java'), 'utf8');
  check('overlay file token substituted', overlay.includes('package com.modforge.ruby_mod;'));
  const props = fs.readFileSync(path.join(dest, 'gradle.properties'), 'utf8');
  check('gradle.properties pins substituted', props.includes('minecraft_version=1.21.11') && props.includes('mod_id=ruby_mod'));
  check('no template.json in workspace', !fs.existsSync(path.join(dest, 'template.json')));
  let threw = false;
  try {
    assembleWorkspace({ templateDir: path.join(config.templatesDir, 'fabric-1.21.11'), destDir: dest, vars: { modId: 'x2' }, files: new Map([['../evil.txt', 'x']]) });
  } catch { threw = true; }
  check('path traversal rejected', threw);
}

// ---------- 2. error parser ----------
console.log('\n[2] build error parser');
{
  const sample = `> Task :compileJava FAILED\n/w/src/main/java/com/modforge/t/ModItems.java:14: error: cannot find symbol\n        Registry.register(BuiltInRegistries.ITEMS, key, item);\n                                           ^\n1 error\n` + 'noise\n'.repeat(5000);
  const report = parseBuildErrors(sample);
  check('report contains file:line', report.includes('ModItems.java:14: error'));
  check('report under 6KB+slack', Buffer.byteLength(report) < 7000, `${Buffer.byteLength(report)} bytes`);
  const fallback = parseBuildErrors('some random failure\nwithout javac lines');
  check('fallback uses log tail', fallback.includes('without javac lines'));
}

// ---------- 3. texture renderer ----------
console.log('\n[3] texture renderer');
{
  const rows = Array.from({ length: 16 }, (_, y) => (y < 8 ? 'r' : '.').repeat(16));
  const res = renderTexture({ size: 16, palette: { r: '#a01515', '.': 'transparent' }, rows });
  check('renders png', Boolean(res.png) && res.png.slice(1, 4).toString() === 'PNG');
  const { PNG } = await import('pngjs');
  const img = PNG.sync.read(res.png);
  check('pixel color correct', img.data[0] === 0xa0 && img.data[1] === 0x15 && img.data[3] === 255);
  check('transparent pixel', img.data[(8 * 16) * 4 + 3] === 0);
  check('rejects bad row length', Boolean(renderTexture({ size: 16, palette: { r: '#fff000' }, rows: ['r'] }).error));
  check('rejects unknown palette char', Boolean(renderTexture({ size: 16, palette: { r: '#a01515' }, rows: Array(16).fill('x'.repeat(16)) }).error));
}

// ---------- 4. resource-pack zipper ----------
console.log('\n[4] resource pack zipper');
{
  const png = renderTexture({ size: 16, palette: { r: '#a01515', '.': 'transparent' }, rows: Array(16).fill('r'.repeat(16)) }).png;
  const zip = await packResourcePack(new Map([
    ['assets/minecraft/textures/item/diamond_sword.png', png],
    ['ignored/evil.txt', 'nope'],
  ]), { description: 'test pack', packFormat: 75 });
  check('zip has magic', zip.slice(0, 2).toString() === 'PK');
  check('zip lists pack.mcmeta', zip.includes('pack.mcmeta'));
  check('zip lists texture', zip.includes('assets/minecraft/textures/item/diamond_sword.png'));
  check('zip excludes non-pack files', !zip.includes('evil.txt'));
}

// ---------- 5. build pipeline ----------
console.log(`\n[5] build pipeline (${realBuild ? 'REAL gradle' : 'stub gradle'})`);
{
  db.prepare("INSERT INTO users (email, pass_hash) VALUES ('verify@test', 'x:y')").run();
  const user = db.prepare("SELECT * FROM users WHERE email='verify@test'").get();
  const info = db.prepare("INSERT INTO projects (user_id, name, kind, mod_id) VALUES (?, 'Verify', 'mod', 'testmod')").run(user.id);
  const project = db.prepare('SELECT * FROM projects WHERE id=?').get(info.lastInsertRowid);
  ws.writeFile(project.id, 'src/main/java/com/modforge/__PKG_ID__/ModItems.java',
    fs.readFileSync(path.join(here, 'fixtures', 'ruby-mod', 'ModItems.java'), 'utf8'));
  ws.writeFile(project.id, 'src/main/java/com/modforge/__PKG_ID__/ModEntry.java', `package com.modforge.__PKG_ID__;

import net.fabricmc.api.ModInitializer;

public class ModEntry implements ModInitializer {
    public static final String MOD_ID = "__MOD_ID__";

    @Override
    public void onInitialize() {
        ModItems.init();
    }
}
`);

  const res = await runBuild(project, { onStatus: () => {} });
  check('build succeeds', res.ok === true, JSON.stringify(res).slice(0, 400));
  if (res.ok) {
    check('artifact file exists', fs.existsSync(db.prepare('SELECT disk_path FROM artifacts WHERE id=?').get(res.artifact.id).disk_path));
    check('artifact is a jar', res.artifact.filename.endsWith('.jar'), res.artifact.filename);
    check('job row success', db.prepare('SELECT status FROM jobs WHERE id=?').get(res.jobId).status === 'success');
  }

  if (!realBuild) {
    process.env.MODFORGE_GRADLE = path.join(here, 'fixtures', 'fake-gradle-fail.sh');
    config.gradleCmd = process.env.MODFORGE_GRADLE;
    const fail = await runBuild(project, { onStatus: () => {} });
    check('failed build reports errors', fail.ok === false && fail.errorReport.includes('ModItems.java:14: error'), (fail.errorReport ?? '').slice(0, 200));
  }
}

console.log(failures === 0 ? '\nAll checks passed ✔' : `\n${failures} check(s) FAILED ✘`);
fs.rmSync(process.env.DATA_DIR, { recursive: true, force: true });
process.exit(failures === 0 ? 0 : 1);
