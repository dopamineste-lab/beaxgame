import { isProduction } from './config/env.js';

type Level = 'debug' | 'info' | 'warn' | 'error';

const order: Record<Level, number> = { debug: 10, info: 20, warn: 30, error: 40 };
const threshold = isProduction ? order.info : order.debug;

/**
 * Tiny structured logger. Emits single-line JSON in production (friendly to
 * Render's log aggregation) and readable text in development.
 */
function log(level: Level, msg: string, meta?: Record<string, unknown>): void {
  if (order[level] < threshold) return;
  const ts = new Date().toISOString();
  if (isProduction) {
    process.stdout.write(JSON.stringify({ ts, level, msg, ...meta }) + '\n');
  } else {
    const tag = { debug: '·', info: 'ℹ', warn: '⚠', error: '✖' }[level];
    const extra = meta ? ' ' + JSON.stringify(meta) : '';
    process.stdout.write(`${tag} ${msg}${extra}\n`);
  }
}

export const logger = {
  debug: (msg: string, meta?: Record<string, unknown>) => log('debug', msg, meta),
  info: (msg: string, meta?: Record<string, unknown>) => log('info', msg, meta),
  warn: (msg: string, meta?: Record<string, unknown>) => log('warn', msg, meta),
  error: (msg: string, meta?: Record<string, unknown>) => log('error', msg, meta),
};
