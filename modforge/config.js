import 'dotenv/config';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const env = process.env;

export const config = {
  root,
  port: Number(env.PORT || 3000),
  dataDir: env.DATA_DIR ? path.resolve(env.DATA_DIR) : path.join(root, 'data'),
  templatesDir: path.join(root, 'template'),
  defaultTemplate: env.DEFAULT_TEMPLATE || 'fabric-1.21.11',

  // Build execution. By default each workspace runs its own ./gradlew (the
  // wrapper downloads the pinned Gradle once into gradle-home). Environments
  // that cannot download distributions can point MODFORGE_GRADLE at a system
  // Gradle binary instead.
  gradleCmd: env.MODFORGE_GRADLE || '',
  buildTimeoutMs: Number(env.BUILD_TIMEOUT_MS || 10 * 60 * 1000),
  buildConcurrency: Number(env.BUILD_CONCURRENCY || 1),
  keepBuildDirs: env.KEEP_BUILD_DIRS === '1',

  anthropicKey: env.ANTHROPIC_API_KEY || '',
  openaiCompat: {
    baseUrl: env.OPENAI_COMPAT_BASE_URL || '',
    apiKey: env.OPENAI_COMPAT_API_KEY || 'ollama',
    model: env.OPENAI_COMPAT_MODEL || 'qwen3-coder:30b',
  },

  allowDevUpgrade: env.ALLOW_DEV_UPGRADE === '1',
  stripe: {
    secretKey: env.STRIPE_SECRET_KEY || '',
    webhookSecret: env.STRIPE_WEBHOOK_SECRET || '',
    priceIdPremium: env.STRIPE_PRICE_ID_PREMIUM || '',
  },

  plans: {
    free: {
      label: 'Free',
      provider: env.FREE_PROVIDER || 'anthropic',
      model: env.FREE_MODEL || 'claude-haiku-4-5-20251001',
      maxTokens: 16000,
      messagesPerDay: 20,
      buildsPerDay: 5,
      maxFiles: 6,
      maxFileKB: 24,
      maxIterations: 12,
      maxBuildAttempts: 2,
      texturesInMods: false,
    },
    premium: {
      label: 'Premium',
      provider: env.PREMIUM_PROVIDER || 'anthropic',
      model: env.PREMIUM_MODEL || 'claude-sonnet-5',
      maxTokens: 32000,
      messagesPerDay: 200,
      buildsPerDay: 40,
      maxFiles: 25,
      maxFileKB: 64,
      maxIterations: 24,
      maxBuildAttempts: 4,
      texturesInMods: true,
    },
  },
};

function providerAvailable(provider) {
  if (provider === 'anthropic') return Boolean(config.anthropicKey);
  if (provider === 'openai-compat') return Boolean(config.openaiCompat.baseUrl);
  return false;
}

// Resolve the {provider, model} a plan should use, falling back to whichever
// backend is actually configured so a single-key setup still serves both plans.
export function resolveModel(plan) {
  const cfg = config.plans[plan] || config.plans.free;
  if (providerAvailable(cfg.provider)) return { provider: cfg.provider, model: cfg.model };
  if (providerAvailable('anthropic')) return { provider: 'anthropic', model: cfg.model.startsWith('claude') ? cfg.model : config.plans[plan].model };
  if (providerAvailable('openai-compat')) return { provider: 'openai-compat', model: config.openaiCompat.model };
  throw Object.assign(
    new Error('Kein KI-Backend konfiguriert. Setze ANTHROPIC_API_KEY oder OPENAI_COMPAT_BASE_URL in der .env.'),
    { status: 503 },
  );
}
