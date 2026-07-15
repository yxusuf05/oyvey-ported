// The agent orchestration loop. One run starts per user chat message and
// executes in the background; the browser follows via SSE. ask_user pauses
// the run by persisting the pending tool_use id on the project — the user's
// next message becomes its tool_result and a fresh run resumes the loop.
import path from 'node:path';
import { db } from '../../db.js';
import { config, resolveModel } from '../../config.js';
import { emit } from '../events.js';
import { chat } from './provider.js';
import { toolsFor, dispatchTool } from './tools.js';
import { buildSystemPrompt } from './prompts.js';
import { loadTemplateMeta } from '../build/assemble.js';

function loadMessages(projectId) {
  return db.prepare('SELECT role, content_json FROM messages WHERE project_id=? ORDER BY id').all(projectId)
    .map((r) => ({ role: r.role, content: JSON.parse(r.content_json) }));
}

function saveMessage(projectId, role, content, displayText = null) {
  db.prepare('INSERT INTO messages (project_id, role, content_json, display_text) VALUES (?,?,?,?)')
    .run(projectId, role, JSON.stringify(content), displayText);
}

function setStatus(projectId, status, { pendingToolId = null, pendingResults = null } = {}) {
  db.prepare('UPDATE projects SET status=?, pending_tool_id=?, pending_results=? WHERE id=?')
    .run(status, pendingToolId, pendingResults ? JSON.stringify(pendingResults) : null, projectId);
}

/**
 * Records the incoming user chat message (as plain text or as the answer to a
 * pending ask_user) and kicks off a background agent run.
 * Caller must have checked limits and that no run is active.
 */
export function submitUserMessage(project, user, text) {
  let content = [{ type: 'text', text }];
  if (project.status === 'waiting_user' && project.pending_tool_id) {
    const partial = project.pending_results ? JSON.parse(project.pending_results) : [];
    content = [
      ...partial,
      { type: 'tool_result', tool_use_id: project.pending_tool_id, content: [{ type: 'text', text }] },
    ];
  }
  saveMessage(project.id, 'user', content, text);
  setStatus(project.id, 'running');
  emit(project.id, 'status', { status: 'running' });

  runAgent(project.id, user).catch((e) => {
    console.error(`[agent] run for project ${project.id} crashed:`, e);
    setStatus(project.id, 'idle');
    emit(project.id, 'error', { message: 'Interner Fehler im KI-Lauf: ' + e.message });
    emit(project.id, 'status', { status: 'idle' });
  });
}

async function runAgent(projectId, user) {
  const project = db.prepare('SELECT * FROM projects WHERE id=?').get(projectId);
  const planCfg = config.plans[user.plan] || config.plans.free;
  const { provider, model } = resolveModel(user.plan);
  const templateMeta = loadTemplateMeta(path.join(config.templatesDir, project.template_id));
  const system = buildSystemPrompt({ project, planCfg, templateMeta });
  const tools = toolsFor(project, planCfg);
  const runState = { buildAttempts: 0 };
  const emitP = (type, data) => emit(projectId, type, data);

  for (let iteration = 0; iteration < planCfg.maxIterations; iteration++) {
    const messages = loadMessages(projectId);
    const { content, stopReason } = await chat({
      provider, model, system, messages, tools,
      maxTokens: planCfg.maxTokens,
      onText: (delta) => emitP('text', { delta }),
    });
    saveMessage(projectId, 'assistant', content);
    emitP('message', { role: 'assistant', content });

    if (stopReason !== 'tool_use') {
      setStatus(projectId, 'idle');
      emitP('status', { status: 'idle' });
      return;
    }

    const toolUses = content.filter((b) => b.type === 'tool_use');
    const results = [];
    for (const tu of toolUses) {
      const out = await dispatchTool(tu.name, tu.input ?? {}, {
        project, user, planCfg, runState, emit: emitP,
      });
      if (out.pause) {
        // Suspend: stash results of tools already executed this turn so the
        // resume message can answer every tool_use block.
        setStatus(projectId, 'waiting_user', { pendingToolId: tu.id, pendingResults: results });
        emitP('question', { question: out.question, options: out.options });
        emitP('status', { status: 'waiting_user' });
        return;
      }
      results.push({
        type: 'tool_result',
        tool_use_id: tu.id,
        content: [{ type: 'text', text: out.result }],
        ...(out.isError ? { is_error: true } : {}),
      });
    }
    saveMessage(projectId, 'user', results);
  }

  setStatus(projectId, 'idle');
  emitP('error', { message: 'Die Aufgabe wurde nach zu vielen Schritten abgebrochen (Plan-Limit). Formuliere die Anfrage kleiner oder upgrade auf Premium.' });
  emitP('status', { status: 'idle' });
}
