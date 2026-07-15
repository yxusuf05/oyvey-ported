import { db, consumeUsage } from '../../db.js';
import * as ws from '../workspace.js';
import { runBuild, storeArtifact } from '../build/runner.js';
import { renderTexture } from '../textures/renderer.js';
import { packResourcePack } from '../textures/packer.js';
import { loadTemplateMeta } from '../build/assemble.js';
import { config } from '../../config.js';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const fileTools = [
  {
    name: 'write_file',
    description: 'Create or overwrite a text file in the project workspace. Paths are workspace-relative (e.g. src/main/java/com/modforge/__PKG_ID__/ModItems.java). The tokens __MOD_ID__ and __PKG_ID__ in paths and content are substituted at build time.',
    input_schema: {
      type: 'object',
      properties: { path: { type: 'string' }, content: { type: 'string' } },
      required: ['path', 'content'],
    },
  },
  {
    name: 'read_file',
    description: 'Read a file from the project workspace.',
    input_schema: { type: 'object', properties: { path: { type: 'string' } }, required: ['path'] },
  },
  {
    name: 'list_files',
    description: 'List all files in the project workspace with sizes.',
    input_schema: { type: 'object', properties: {} },
  },
  {
    name: 'delete_file',
    description: 'Delete a file from the project workspace.',
    input_schema: { type: 'object', properties: { path: { type: 'string' } }, required: ['path'] },
  },
];

const askUserTool = {
  name: 'ask_user',
  description: 'Ask the user clarifying questions before or during generation. Pauses the task until the user answers in chat. Batch all open questions into one call. Must be called ALONE (no other tool calls in the same turn).',
  input_schema: {
    type: 'object',
    properties: {
      question: { type: 'string', description: 'The question(s), phrased for a non-programmer, in the user\'s language.' },
      options: { type: 'array', items: { type: 'string' }, description: 'Optional quick-reply choices shown as clickable chips.' },
    },
    required: ['question'],
  },
};

const requestBuildTool = {
  name: 'request_build',
  description: 'Compile the current workspace into a mod .jar. Returns the artifact id on success, or a compact compile-error report to fix. Builds take 1-5 minutes.',
  input_schema: { type: 'object', properties: {} },
};

const writeTextureTool = {
  name: 'write_texture',
  description: 'Create a PNG texture from pixel art. Provide a palette mapping single characters to colors and one string per pixel row. Example 16x16: palette {".": "transparent", "r": "#a01515"}, rows: 16 strings of 16 chars each.',
  input_schema: {
    type: 'object',
    properties: {
      path: { type: 'string', description: 'Target path, e.g. assets/minecraft/textures/item/diamond_sword.png (resource pack) or src/main/resources/assets/__MOD_ID__/textures/item/ruby.png (mod)' },
      size: { type: 'integer', enum: [16, 32] },
      palette: { type: 'object', additionalProperties: { type: 'string' }, description: 'single char -> "#rrggbb", "#rrggbbaa" or "transparent"' },
      rows: { type: 'array', items: { type: 'string' } },
    },
    required: ['path', 'size', 'palette', 'rows'],
  },
};

const packTool = {
  name: 'package_resourcepack',
  description: 'Package the workspace (pack.mcmeta, pack.png, assets/**) into a resource-pack .zip. Returns the artifact id.',
  input_schema: { type: 'object', properties: {} },
};

const deliverTool = {
  name: 'deliver',
  description: 'Deliver a finished artifact to the user as a download card in the chat. Call once the build/pack succeeded.',
  input_schema: {
    type: 'object',
    properties: {
      artifact_id: { type: 'integer' },
      summary: { type: 'string', description: 'One friendly sentence in the user\'s language describing what was built.' },
    },
    required: ['artifact_id', 'summary'],
  },
};

export function toolsFor(project, planCfg) {
  if (project.kind === 'resourcepack') {
    return [...fileTools, askUserTool, writeTextureTool, packTool, deliverTool];
  }
  const tools = [...fileTools, askUserTool, requestBuildTool, deliverTool];
  if (planCfg.texturesInMods) tools.push(writeTextureTool);
  return tools;
}

const err = (message) => ({ result: message, isError: true });

/**
 * Executes one tool call. Returns:
 *   { result: string, isError?: true }        — normal tool_result content
 *   { pause: true, question, options }        — ask_user: suspend the run
 * ctx: { project, user, planCfg, runState:{buildAttempts}, emit }
 */
export async function dispatchTool(name, input, ctx) {
  const { project, user, planCfg, runState, emit } = ctx;
  try {
    switch (name) {
      case 'write_file': {
        const p = ws.normalizePath(input.path);
        if (!p) return err(`Path not allowed: "${input.path}". Allowed: src/main/java/**, src/main/resources/**, assets/**, pack.mcmeta, pack.png.`);
        const kb = Buffer.byteLength(String(input.content), 'utf8') / 1024;
        if (kb > planCfg.maxFileKB) return err(`File too large (${kb.toFixed(1)} KB). Your plan allows ${planCfg.maxFileKB} KB per file — split or simplify.`);
        if (!ws.readFile(project.id, p) && ws.fileCount(project.id) >= planCfg.maxFiles) {
          return err(`Project file limit reached (${planCfg.maxFiles} files on the ${planCfg.label} plan). Delete a file first or simplify the project.`);
        }
        ws.writeFile(project.id, p, String(input.content));
        emit('tool', { name, path: p });
        return { result: `wrote ${p} (${kb.toFixed(1)} KB)` };
      }
      case 'read_file': {
        const p = ws.normalizePath(input.path);
        const file = p && ws.readFile(project.id, p);
        if (!file) return err(`no such file: ${input.path}`);
        return { result: file.isBinary ? `(binary file, ${file.content.length} bytes)` : file.content };
      }
      case 'list_files': {
        const files = ws.listFiles(project.id);
        return { result: files.length ? files.map((f) => `${f.path} (${f.size} B${f.is_binary ? ', binary' : ''})`).join('\n') : '(workspace is empty)' };
      }
      case 'delete_file': {
        const p = ws.normalizePath(input.path);
        if (!p || !ws.deleteFile(project.id, p)) return err(`no such file: ${input.path}`);
        emit('tool', { name, path: p });
        return { result: `deleted ${p}` };
      }
      case 'ask_user': {
        return { pause: true, question: String(input.question ?? ''), options: Array.isArray(input.options) ? input.options.map(String).slice(0, 4) : [] };
      }
      case 'request_build': {
        if (runState.buildAttempts >= planCfg.maxBuildAttempts) {
          return err(`Build attempt limit for this task reached (${planCfg.maxBuildAttempts} on the ${planCfg.label} plan). Explain the remaining problem to the user in simple terms and stop.`);
        }
        if (!consumeUsage(user.id, 'builds_used', planCfg.buildsPerDay)) {
          return err(`Daily build limit reached (${planCfg.buildsPerDay}/day on the ${planCfg.label} plan). Tell the user to try again tomorrow or upgrade.`);
        }
        runState.buildAttempts++;
        const res = await runBuild(project, {
          onStatus: (status, extra) => emit('job', { status, ...extra }),
        });
        if (res.ok) {
          return { result: `BUILD SUCCESS. artifact_id=${res.artifact.id} filename=${res.artifact.filename} size=${res.artifact.size} bytes. Call deliver with this artifact_id.` };
        }
        return err(`BUILD FAILED (attempt ${runState.buildAttempts}/${planCfg.maxBuildAttempts}). Fix these errors, then build again:\n\n${res.errorReport}`);
      }
      case 'write_texture': {
        if (project.kind === 'mod' && !planCfg.texturesInMods) {
          return err('Textures in mods are a Premium feature on this account.');
        }
        const p = ws.normalizePath(input.path);
        if (!p || !p.endsWith('.png')) return err(`Path not allowed for a texture: "${input.path}" (must be a .png under assets/ or src/main/resources/assets/).`);
        const rendered = renderTexture({ size: input.size, palette: input.palette, rows: input.rows });
        if (rendered.error) return err(`Texture invalid: ${rendered.error}`);
        ws.writeFile(project.id, p, rendered.png, { isBinary: true });
        emit('tool', { name, path: p });
        return { result: `rendered ${input.size}x${input.size} texture -> ${p}` };
      }
      case 'package_resourcepack': {
        const meta = loadTemplateMeta(path.join(config.templatesDir, project.template_id));
        const files = ws.filesAsMap(project.id);
        if (![...files.keys()].some((k) => k.startsWith('assets/'))) {
          return err('The workspace has no assets/ files yet — create textures first.');
        }
        const jobInfo = db.prepare("INSERT INTO jobs (project_id, type, status) VALUES (?, 'pack', 'running')").run(project.id);
        const zip = await packResourcePack(files, {
          description: `${project.name} — created with ModForge`,
          packFormat: meta.pack_format,
        });
        const tmp = path.join(os.tmpdir(), `modforge-pack-${Date.now()}.zip`);
        fs.writeFileSync(tmp, zip);
        const filename = `${project.mod_id}-resourcepack.zip`;
        const artifact = storeArtifact(project.id, tmp, filename);
        fs.rmSync(tmp, { force: true });
        db.prepare("UPDATE jobs SET status='success', artifact_id=?, finished_at=datetime('now') WHERE id=?").run(artifact.id, Number(jobInfo.lastInsertRowid));
        emit('job', { status: 'success', artifact });
        return { result: `PACKED. artifact_id=${artifact.id} filename=${filename} size=${artifact.size} bytes. Call deliver with this artifact_id.` };
      }
      case 'deliver': {
        const artifact = db.prepare('SELECT * FROM artifacts WHERE id=? AND project_id=?').get(input.artifact_id, project.id);
        if (!artifact) return err(`no artifact with id ${input.artifact_id} in this project`);
        db.prepare('UPDATE artifacts SET delivered=1, summary=? WHERE id=?').run(String(input.summary ?? ''), artifact.id);
        emit('artifact', { id: artifact.id, filename: artifact.filename, size: artifact.size, summary: String(input.summary ?? '') });
        return { result: 'delivered — the user now sees the download card.' };
      }
      default:
        return err(`unknown tool: ${name}`);
    }
  } catch (e) {
    return err(`tool ${name} failed: ${e.message}`);
  }
}
