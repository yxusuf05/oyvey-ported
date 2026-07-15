#!/usr/bin/env node
// Mock OpenAI-compatible /v1/chat/completions endpoint for end-to-end tests.
// Plays a scripted mod-building conversation: ask_user -> write files ->
// request_build -> deliver -> done. Lets the whole ModForge stack (provider
// conversion, agent loop, pause/resume, build queue, SSE, artifacts) run
// without a real LLM.
import http from 'node:http';

const PORT = Number(process.env.MOCK_PORT || 4519);

function sse(res, events) {
  res.writeHead(200, { 'Content-Type': 'text/event-stream' });
  for (const ev of events) res.write(`data: ${JSON.stringify(ev)}\n\n`);
  res.write('data: [DONE]\n\n');
  res.end();
}

const chunk = (delta, finish = null) => ({
  id: 'mock', object: 'chat.completion.chunk', choices: [{ index: 0, delta, finish_reason: finish }],
});

const toolCall = (id, name, args) => chunk({
  tool_calls: [{ index: 0, id, type: 'function', function: { name, arguments: JSON.stringify(args) } }],
});

const MOD_ITEMS = `package com.modforge.__PKG_ID__;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.util.function.Function;

public final class ModItems {
    public static final Item RUBY = register("ruby", Item::new, new Item.Properties());

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(ModEntry.MOD_ID, name));
        Item item = factory.apply(props.setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void init() {}
}
`;

http.createServer((req, res) => {
  if (!req.url.endsWith('/chat/completions')) { res.writeHead(404); res.end(); return; }
  let body = '';
  req.on('data', (c) => { body += c; });
  req.on('end', () => {
    const { messages } = JSON.parse(body);
    const toolMsgs = messages.filter((m) => m.role === 'tool');
    const asked = messages.some((m) => m.tool_calls?.some((t) => t.function.name === 'ask_user'));
    const wrote = messages.some((m) => m.tool_calls?.some((t) => t.function.name === 'write_file'));
    const built = messages.some((m) => m.tool_calls?.some((t) => t.function.name === 'request_build'));
    const delivered = messages.some((m) => m.tool_calls?.some((t) => t.function.name === 'deliver'));

    if (!asked) {
      return sse(res, [
        chunk({ role: 'assistant', content: 'Kurze Rückfrage, bevor ich loslege: ' }),
        toolCall('call_ask1', 'ask_user', { question: 'Soll der Rubin auch im Kreativ-Inventar auftauchen?', options: ['Ja', 'Nein'] }),
        chunk({}, 'tool_calls'),
      ]);
    }
    if (!wrote) {
      return sse(res, [
        chunk({ role: 'assistant', content: 'Alles klar, ich schreibe den Code…' }),
        toolCall('call_w1', 'write_file', { path: 'src/main/java/com/modforge/__PKG_ID__/ModItems.java', content: MOD_ITEMS }),
        chunk({}, 'tool_calls'),
      ]);
    }
    if (!built) {
      return sse(res, [toolCall('call_b1', 'request_build', {}), chunk({}, 'tool_calls')]);
    }
    if (!delivered) {
      const m = toolMsgs.map((t) => t.content).join('\n').match(/artifact_id=(\d+)/);
      const artifactId = m ? Number(m[1]) : 1;
      return sse(res, [
        toolCall('call_d1', 'deliver', { artifact_id: artifactId, summary: 'Dein Rubin-Mod ist fertig!' }),
        chunk({}, 'tool_calls'),
      ]);
    }
    return sse(res, [chunk({ role: 'assistant', content: 'Fertig! Die Jar liegt oben als Download bereit. 🎉' }), chunk({}, 'stop')]);
  });
}).listen(PORT, () => console.log(`mock-openai listening on :${PORT}`));
