#!/usr/bin/env node
// One-shot: assembles the pristine template with a fixture mod id and builds
// it once, so the shared GRADLE_USER_HOME cache holds the Gradle distribution
// and all Fabric/Loom dependencies. Run at install time; later user builds
// then take ~30-90s instead of many minutes.
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { assembleWorkspace } from '../src/build/assemble.js';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const dataDir = process.env.DATA_DIR || path.join(root, 'data');
const templateDir = path.join(root, 'template', 'fabric-1.21.11');
const workDir = path.join(dataDir, 'builds', 'warmup');
const gradleHome = path.join(dataDir, 'gradle-home');

fs.mkdirSync(gradleHome, { recursive: true });
const { tokens } = assembleWorkspace({
  templateDir,
  destDir: workDir,
  vars: { modId: 'warmup', modName: 'Warmup', modVersion: '1.0.0', modDescription: 'cache warmup build' },
});

console.log(`[warm-gradle] building template in ${workDir} (this downloads Gradle + all Fabric deps on first run)…`);
const started = Date.now();
const child = spawn('sh', ['gradlew', 'build', '--no-daemon', '--console=plain'], {
  cwd: workDir,
  env: { ...process.env, GRADLE_USER_HOME: gradleHome },
  stdio: 'inherit',
});

child.on('exit', (code) => {
  const secs = Math.round((Date.now() - started) / 1000);
  if (code !== 0) {
    console.error(`[warm-gradle] FAILED after ${secs}s (exit ${code})`);
    process.exit(1);
  }
  const libs = path.join(workDir, 'build', 'libs');
  const jars = fs.readdirSync(libs).filter((f) => f.endsWith('.jar') && !f.includes('-sources'));
  if (jars.length === 0) {
    console.error('[warm-gradle] build succeeded but no jar found in build/libs');
    process.exit(1);
  }
  const jar = path.join(libs, jars[0]);
  console.log(`[warm-gradle] OK in ${secs}s → ${jar} (${fs.statSync(jar).size} bytes, mod id "${tokens.__MOD_ID__}")`);
});
