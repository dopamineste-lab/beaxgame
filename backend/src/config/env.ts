import { z } from 'zod';

/**
 * Typed, validated environment configuration.
 *
 * Every field has a development-safe default so the server boots with an empty
 * `.env`. `REDIS_URL` / `DATABASE_URL` are intentionally optional — when absent
 * the app runs in "dev mode" (in-memory Redis shim, persistence disabled).
 */
const schema = z.object({
  PORT: z.coerce.number().int().positive().default(8080),
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  JWT_SECRET: z.string().min(1).default('dev-insecure-secret-change-me'),
  JWT_TTL_SECONDS: z.coerce.number().int().positive().default(604800),
  CORS_ORIGIN: z.string().default('*'),
  REDIS_URL: z.string().optional().transform((v) => (v && v.length > 0 ? v : undefined)),
  DATABASE_URL: z.string().optional().transform((v) => (v && v.length > 0 ? v : undefined)),
  RECONNECT_GRACE_MS: z.coerce.number().int().nonnegative().default(15_000),
  QUEUE_TIMEOUT_MS: z.coerce.number().int().positive().default(60_000),
});

const parsed = schema.safeParse(process.env);

if (!parsed.success) {
  // Fail fast with a readable message rather than crashing deep in a handler.
  console.error('❌ Invalid environment configuration:');
  console.error(parsed.error.flatten().fieldErrors);
  process.exit(1);
}

export const env = parsed.data;

/** True when no external Redis is configured (uses in-memory shim). */
export const usingRedisShim = env.REDIS_URL === undefined;

/** True when no Postgres is configured (persistence is skipped). */
export const persistenceDisabled = env.DATABASE_URL === undefined;

export const isProduction = env.NODE_ENV === 'production';

if (isProduction && env.JWT_SECRET === 'dev-insecure-secret-change-me') {
  console.error('❌ Refusing to start in production with the default JWT_SECRET.');
  process.exit(1);
}
