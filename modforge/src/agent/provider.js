// Model-backend abstraction. Conversations are stored in Anthropic content-
// block form (text / tool_use / tool_result); the OpenAI-compatible provider
// (Ollama, LM Studio, any /v1/chat/completions endpoint) converts on the way
// in and out, so the rest of the system never cares which backend runs.
import Anthropic from '@anthropic-ai/sdk';
import OpenAI from 'openai';
import { config } from '../../config.js';

let anthropicClient = null;
let openaiClient = null;

function anthropic() {
  anthropicClient ??= new Anthropic({ apiKey: config.anthropicKey });
  return anthropicClient;
}

function openaiCompat() {
  openaiClient ??= new OpenAI({ baseURL: config.openaiCompat.baseUrl, apiKey: config.openaiCompat.apiKey });
  return openaiClient;
}

/**
 * @param {object} p
 * @param {'anthropic'|'openai-compat'} p.provider
 * @param {string} p.model
 * @param {string} p.system
 * @param {Array} p.messages  Anthropic-shaped {role, content:[blocks]}
 * @param {Array} p.tools     Anthropic-shaped tool definitions
 * @param {number} p.maxTokens
 * @param {(delta: string) => void} [p.onText]
 * @returns {Promise<{content: Array, stopReason: string}>} Anthropic-shaped assistant content
 */
export async function chat({ provider, model, system, messages, tools, maxTokens, onText }) {
  if (provider === 'anthropic') return chatAnthropic({ model, system, messages, tools, maxTokens, onText });
  if (provider === 'openai-compat') return chatOpenAI({ model, system, messages, tools, maxTokens, onText });
  throw new Error(`unknown provider: ${provider}`);
}

async function chatAnthropic({ model, system, messages, tools, maxTokens, onText }) {
  // No temperature/top_p/thinking: Sonnet 5 rejects sampling params and runs
  // adaptive thinking by default. cache_control on the stable system prompt
  // makes build-fix iterations cheap.
  const stream = anthropic().messages.stream({
    model,
    max_tokens: maxTokens,
    system: [{ type: 'text', text: system, cache_control: { type: 'ephemeral' } }],
    tools,
    messages,
  });
  if (onText) stream.on('text', onText);
  const final = await stream.finalMessage();
  return { content: final.content, stopReason: final.stop_reason };
}

function toOpenAITools(tools) {
  return tools.map((t) => ({
    type: 'function',
    function: { name: t.name, description: t.description, parameters: t.input_schema },
  }));
}

function toOpenAIMessages(system, messages) {
  const out = [{ role: 'system', content: system }];
  for (const msg of messages) {
    const blocks = typeof msg.content === 'string' ? [{ type: 'text', text: msg.content }] : msg.content;
    if (msg.role === 'assistant') {
      const text = blocks.filter((b) => b.type === 'text').map((b) => b.text).join('\n');
      const toolCalls = blocks.filter((b) => b.type === 'tool_use').map((b) => ({
        id: b.id,
        type: 'function',
        function: { name: b.name, arguments: JSON.stringify(b.input) },
      }));
      const m = { role: 'assistant', content: text || null };
      if (toolCalls.length) m.tool_calls = toolCalls;
      out.push(m);
    } else {
      for (const b of blocks) {
        if (b.type === 'tool_result') {
          const content = typeof b.content === 'string'
            ? b.content
            : (b.content ?? []).map((c) => c.text ?? '').join('\n');
          out.push({ role: 'tool', tool_call_id: b.tool_use_id, content });
        } else if (b.type === 'text') {
          out.push({ role: 'user', content: b.text });
        }
      }
    }
  }
  return out;
}

async function chatOpenAI({ model, system, messages, tools, maxTokens, onText }) {
  const stream = await openaiCompat().chat.completions.create({
    model,
    max_tokens: maxTokens,
    messages: toOpenAIMessages(system, messages),
    tools: toOpenAITools(tools),
    stream: true,
  });

  let text = '';
  const toolCalls = new Map(); // index -> {id, name, args}
  let finishReason = 'stop';
  for await (const chunk of stream) {
    const choice = chunk.choices?.[0];
    if (!choice) continue;
    if (choice.delta?.content) {
      text += choice.delta.content;
      onText?.(choice.delta.content);
    }
    for (const tc of choice.delta?.tool_calls ?? []) {
      const acc = toolCalls.get(tc.index) ?? { id: '', name: '', args: '' };
      if (tc.id) acc.id = tc.id;
      if (tc.function?.name) acc.name += tc.function.name;
      if (tc.function?.arguments) acc.args += tc.function.arguments;
      toolCalls.set(tc.index, acc);
    }
    if (choice.finish_reason) finishReason = choice.finish_reason;
  }

  const content = [];
  if (text) content.push({ type: 'text', text });
  let i = 0;
  for (const acc of toolCalls.values()) {
    let input = {};
    try { input = JSON.parse(acc.args || '{}'); } catch { /* leave {} — dispatcher will report schema error */ }
    content.push({ type: 'tool_use', id: acc.id || `call_${Date.now()}_${i++}`, name: acc.name, input });
  }
  return { content, stopReason: toolCalls.size > 0 || finishReason === 'tool_calls' ? 'tool_use' : 'end_turn' };
}
