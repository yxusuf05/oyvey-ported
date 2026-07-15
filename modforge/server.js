import express from 'express';
import path from 'node:path';
import { config } from './config.js';
import './db.js';
import { api } from './src/routes.js';
import { billingRouter, webhookHandler } from './src/billing.js';

const app = express();
app.disable('x-powered-by');

// Stripe webhook needs the raw body for signature verification — mount it
// before the JSON parser.
app.post('/api/billing/webhook', express.raw({ type: 'application/json' }), webhookHandler);

app.use(express.json({ limit: '256kb' }));
app.use('/api/billing', billingRouter);
app.use('/api', api);
app.use(express.static(path.join(config.root, 'public')));

// Central error handler so thrown httpError(status, message) becomes JSON.
app.use((err, req, res, next) => {
  if (res.headersSent) return next(err);
  const status = err.status ?? 500;
  if (status >= 500) console.error(err);
  res.status(status).json({ error: status >= 500 ? 'Interner Serverfehler.' : err.message });
});

app.listen(config.port, () => {
  console.log(`ModForge läuft auf http://localhost:${config.port}`);
  console.log(`KI-Backend: anthropic=${config.anthropicKey ? 'konfiguriert' : '—'} openai-compat=${config.openaiCompat.baseUrl || '—'}`);
});
