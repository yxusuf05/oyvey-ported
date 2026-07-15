'use strict';

const $ = (id) => document.getElementById(id);
let me = null;
let currentProject = null;
let eventSource = null;
let streamEl = null; // element receiving live text deltas

// ---------- API helper ----------
async function api(path, opts = {}) {
  const res = await fetch('/api' + path, {
    headers: { 'Content-Type': 'application/json' },
    ...opts,
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw Object.assign(new Error(data.error || res.statusText), { status: res.status, data });
  return data;
}

// ---------- auth ----------
async function boot() {
  try {
    me = await api('/me');
    $('auth-view').classList.add('hidden');
    $('app-view').classList.remove('hidden');
    renderAccount();
    await loadProjects();
  } catch {
    $('auth-view').classList.remove('hidden');
    $('app-view').classList.add('hidden');
  }
}

$('auth-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const mode = e.submitter?.dataset.mode || 'login';
  $('auth-error').textContent = '';
  try {
    await api(`/auth/${mode}`, { method: 'POST', body: { email: $('auth-email').value, password: $('auth-password').value } });
    await boot();
  } catch (err) {
    $('auth-error').textContent = err.message;
  }
});

$('logout-btn').addEventListener('click', async () => {
  await api('/auth/logout', { method: 'POST' });
  location.reload();
});

function renderAccount() {
  $('plan-badge').textContent = me.plan === 'premium' ? '⭐ Premium' : 'Free-Plan';
  $('plan-badge').className = 'plan-badge ' + me.plan;
  $('usage-info').textContent = `Heute: ${me.usage.messages_used}/${me.limits.messagesPerDay} Nachrichten · ${me.usage.builds_used}/${me.limits.buildsPerDay} Builds`;
  $('upgrade-btn').classList.toggle('hidden', me.plan === 'premium');
}

async function refreshMe() {
  try { me = await api('/me'); renderAccount(); } catch { /* ignore */ }
}

$('upgrade-btn').addEventListener('click', async () => {
  const res = await api('/billing/checkout', { method: 'POST' });
  if (res.url) { location.href = res.url; return; }
  if (res.stub) {
    if (me.devUpgrade && confirm(res.message + '\n\nDev-Upgrade jetzt ausführen?')) {
      await api('/billing/dev-upgrade', { method: 'POST' });
      await refreshMe();
    } else {
      alert(res.message);
    }
  }
});

// ---------- projects ----------
async function loadProjects() {
  const projects = await api('/projects');
  const list = $('project-list');
  list.innerHTML = '';
  for (const p of projects) {
    const div = document.createElement('div');
    div.className = 'project-item' + (currentProject?.id === p.id ? ' active' : '');
    div.innerHTML = `<span class="kind">${p.kind === 'mod' ? '🧩' : '🎨'}</span><span class="name"></span>`;
    div.querySelector('.name').textContent = p.name;
    div.addEventListener('click', () => openProject(p.id));
    list.appendChild(div);
  }
}

$('new-project').addEventListener('click', () => $('project-dialog').showModal());
$('project-cancel').addEventListener('click', () => $('project-dialog').close());
$('project-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const p = await api('/projects', { method: 'POST', body: { name: $('project-name').value, kind: $('project-kind').value } });
  $('project-dialog').close();
  $('project-name').value = '';
  await loadProjects();
  openProject(p.id);
});

async function openProject(id) {
  const p = await api(`/projects/${id}`);
  currentProject = p;
  $('empty-state').classList.add('hidden');
  $('chat-view').classList.remove('hidden');
  $('chat-header').textContent = `${p.kind === 'mod' ? '🧩' : '🎨'} ${p.name} — ${p.kind === 'mod' ? 'Fabric-Mod' : 'Resource-Pack'} (${p.mod_id})`;
  const box = $('messages');
  box.innerHTML = '';
  for (const m of p.messages) addMessage(m.role, m.text);
  for (const a of p.artifacts) addArtifactCard(a);
  showQuestion(p.question);
  setStatus(p.status);
  connectEvents(id);
  loadProjects();
  scrollDown();
}

// ---------- chat rendering ----------
function addMessage(role, text) {
  const div = document.createElement('div');
  div.className = 'msg ' + role;
  div.textContent = text;
  $('messages').appendChild(div);
  return div;
}

function addArtifactCard(a) {
  const div = document.createElement('div');
  div.className = 'artifact-card';
  const kb = (a.size / 1024).toFixed(1);
  div.innerHTML = `<div class="icon">📦</div><div class="meta"><div class="fname"></div><div class="fsize">${kb} KB</div><div class="fsummary"></div></div><a class="primary dl" href="/api/artifacts/${a.id}/download">⬇ Herunterladen</a>`;
  div.querySelector('.fname').textContent = a.filename;
  div.querySelector('.fsummary').textContent = a.summary || '';
  $('messages').appendChild(div);
}

function showQuestion(q) {
  const chips = $('question-chips');
  chips.innerHTML = '';
  if (!q || !q.options?.length) { chips.classList.add('hidden'); return; }
  for (const opt of q.options) {
    const b = document.createElement('button');
    b.className = 'chip';
    b.textContent = opt;
    b.addEventListener('click', () => sendMessage(opt));
    chips.appendChild(b);
  }
  chips.classList.remove('hidden');
}

let statusTimer = null;
function setStatus(status, extra) {
  const bar = $('status-bar');
  clearInterval(statusTimer);
  if (status === 'running') {
    bar.textContent = '🤖 Die KI arbeitet…';
    bar.classList.remove('hidden');
  } else if (status === 'building') {
    const start = Date.now();
    const tick = () => {
      const s = Math.floor((Date.now() - start) / 1000);
      bar.textContent = `🔨 Baue die Mod… ${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} (kann einige Minuten dauern)`;
    };
    tick();
    statusTimer = setInterval(tick, 1000);
    bar.classList.remove('hidden');
  } else if (status === 'queued') {
    bar.textContent = '⏳ Build in Warteschlange…';
    bar.classList.remove('hidden');
  } else {
    bar.classList.add('hidden');
  }
}

function scrollDown() {
  const box = $('messages');
  box.scrollTop = box.scrollHeight;
}

// ---------- SSE ----------
function connectEvents(projectId) {
  eventSource?.close();
  streamEl = null;
  eventSource = new EventSource(`/api/projects/${projectId}/events`);
  eventSource.onmessage = (e) => {
    const ev = JSON.parse(e.data);
    handleEvent(ev);
  };
  eventSource.onerror = () => {
    // EventSource reconnects itself; refresh state once it's back.
    setTimeout(() => { if (eventSource.readyState === EventSource.OPEN && currentProject) openProject(currentProject.id); }, 3000);
  };
}

function handleEvent(ev) {
  switch (ev.type) {
    case 'text':
      if (!streamEl) streamEl = addMessage('assistant', '');
      streamEl.textContent += ev.delta;
      scrollDown();
      break;
    case 'message':
      streamEl = null; // message complete; next text starts a new bubble
      break;
    case 'status':
      setStatus(ev.status);
      if (ev.status !== 'running') refreshMe();
      break;
    case 'job':
      if (ev.status === 'queued') setStatus('queued');
      else if (ev.status === 'running') setStatus('building');
      else setStatus('running');
      break;
    case 'question':
      showQuestion(ev);
      scrollDown();
      break;
    case 'artifact':
      addArtifactCard(ev);
      scrollDown();
      break;
    case 'tool':
      break; // file activity — intentionally quiet in the UI
    case 'error':
      addMessage('error', ev.message);
      scrollDown();
      break;
  }
}

// ---------- composer ----------
$('chat-form').addEventListener('submit', (e) => {
  e.preventDefault();
  const text = $('chat-input').value.trim();
  if (text) sendMessage(text);
});

$('chat-input').addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    $('chat-form').requestSubmit();
  }
});

async function sendMessage(text) {
  if (!currentProject) return;
  try {
    await api(`/projects/${currentProject.id}/messages`, { method: 'POST', body: { text } });
    addMessage('user', text);
    $('chat-input').value = '';
    showQuestion(null);
    streamEl = null;
    refreshMe();
    scrollDown();
  } catch (err) {
    addMessage('error', err.message + (err.data?.upgrade ? ' — Premium hat deutlich höhere Limits.' : ''));
  }
}

boot();
