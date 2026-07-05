import pg from 'pg';
import { env, isProduction, persistenceDisabled } from '../config/env.js';
import { logger } from '../logger.js';

/**
 * PostgreSQL connection pool. When `DATABASE_URL` is unset the pool is `null`
 * and persistence is silently skipped (dev mode). All persistence call sites
 * must tolerate a null pool via {@link isPersistenceEnabled}.
 */
export const pool: pg.Pool | null = persistenceDisabled
  ? null
  : new pg.Pool({
      connectionString: env.DATABASE_URL,
      // Render's managed Postgres requires TLS; local URLs generally do not.
      ssl: isProduction ? { rejectUnauthorized: false } : undefined,
      max: 10,
      idleTimeoutMillis: 30_000,
      connectionTimeoutMillis: 5_000,
    });

if (pool) {
  pool.on('error', (err) => logger.error('Postgres pool error', { error: err.message }));
} else {
  logger.warn('Persistence disabled (DATABASE_URL not set). Matches will not be recorded.');
}

export function isPersistenceEnabled(): boolean {
  return pool !== null;
}

/** Run a parameterized query. No-op-safe callers should check isPersistenceEnabled first. */
export async function query<T extends pg.QueryResultRow = pg.QueryResultRow>(
  text: string,
  params?: unknown[],
): Promise<pg.QueryResult<T>> {
  if (!pool) throw new Error('query() called while persistence is disabled');
  return pool.query<T>(text, params);
}

export async function pingDatabase(): Promise<boolean> {
  if (!pool) return false;
  try {
    await pool.query('SELECT 1');
    return true;
  } catch {
    return false;
  }
}
