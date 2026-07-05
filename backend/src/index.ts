import { buildServer } from './app.js';
import { env } from './config/env.js';
import { logger } from './logger.js';
import { pool } from './db/pool.js';
import { redis } from './redis/client.js';

/**
 * Process entrypoint: build the server, start listening, and wire graceful
 * shutdown so in-flight requests drain and connections close cleanly on SIGTERM
 * (which Render sends on deploy/scale-down).
 */
async function main(): Promise<void> {
  const { app, io } = await buildServer();

  await app.listen({ port: env.PORT, host: '0.0.0.0' });
  logger.info(`OX Arena backend listening on :${env.PORT}`, { env: env.NODE_ENV });

  let shuttingDown = false;
  const shutdown = async (signal: string): Promise<void> => {
    if (shuttingDown) return;
    shuttingDown = true;
    logger.info(`Received ${signal}, shutting down gracefully…`);
    try {
      io.close();
      await app.close();
      await redis.quit().catch(() => undefined);
      await pool?.end().catch(() => undefined);
      logger.info('Shutdown complete.');
      process.exit(0);
    } catch (err) {
      logger.error('Error during shutdown', { error: err instanceof Error ? err.message : String(err) });
      process.exit(1);
    }
  };

  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('unhandledRejection', (reason) => {
    logger.error('Unhandled promise rejection', { reason: String(reason) });
  });
}

main().catch((err) => {
  logger.error('Fatal startup error', { error: err instanceof Error ? err.message : String(err) });
  process.exit(1);
});
