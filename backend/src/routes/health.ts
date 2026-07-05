import type { FastifyInstance } from 'fastify';
import { persistenceDisabled, usingRedisShim } from '../config/env.js';
import { pingDatabase } from '../db/pool.js';
import { redis } from '../redis/client.js';
import { activeMatchCount } from '../game/gameManager.js';

/**
 * Health & readiness endpoints.
 *  - GET /healthz  liveness: the process is up (always 200 if reachable).
 *  - GET /readyz   readiness: dependencies (Redis, Postgres) are usable.
 * Render uses these for zero-downtime deploys and auto-restart.
 */
export async function registerHealthRoutes(app: FastifyInstance): Promise<void> {
  app.get('/healthz', async () => ({ status: 'ok', activeMatches: activeMatchCount() }));

  app.get('/readyz', async (_req, reply) => {
    const redisOk = await redis
      .ping()
      .then((r) => r === 'PONG')
      .catch(() => false);
    const dbOk = persistenceDisabled ? true : await pingDatabase();

    const ready = redisOk && dbOk;
    reply.code(ready ? 200 : 503);
    return {
      status: ready ? 'ready' : 'degraded',
      checks: {
        redis: usingRedisShim ? 'shim' : redisOk ? 'up' : 'down',
        database: persistenceDisabled ? 'disabled' : dbOk ? 'up' : 'down',
      },
    };
  });
}
