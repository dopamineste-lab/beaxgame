import { Redis } from 'ioredis';
import { env, usingRedisShim } from '../config/env.js';
import { logger } from '../logger.js';

/**
 * The subset of Redis operations OX Arena depends on. Both the real ioredis
 * client and the in-memory dev shim satisfy this interface, so the rest of the
 * codebase is agnostic to which is in use.
 */
export interface RedisLike {
  rpush(key: string, value: string): Promise<number>;
  lrange(key: string, start: number, stop: number): Promise<string[]>;
  lrem(key: string, count: number, value: string): Promise<number>;
  llen(key: string): Promise<number>;
  sadd(key: string, value: string): Promise<number>;
  srem(key: string, value: string): Promise<number>;
  sismember(key: string, value: string): Promise<number>;
  set(key: string, value: string, mode?: 'EX', ttlSeconds?: number): Promise<unknown>;
  get(key: string): Promise<string | null>;
  del(key: string): Promise<number>;
  ping(): Promise<string>;
  quit(): Promise<void>;
}

/**
 * In-memory Redis shim used when `REDIS_URL` is unset. Implements exactly the
 * operations above with plain JS structures, enough to run a full local match
 * without any external infrastructure. Single-process only (never for prod).
 */
class InMemoryRedis implements RedisLike {
  private lists = new Map<string, string[]>();
  private sets = new Map<string, Set<string>>();
  private strings = new Map<string, string>();

  async rpush(key: string, value: string): Promise<number> {
    const list = this.lists.get(key) ?? [];
    list.push(value);
    this.lists.set(key, list);
    return list.length;
  }

  async lrange(key: string, start: number, stop: number): Promise<string[]> {
    const list = this.lists.get(key) ?? [];
    // Emulate Redis negative-index / inclusive-stop semantics.
    const end = stop < 0 ? list.length + stop + 1 : stop + 1;
    return list.slice(start, end);
  }

  async lrem(key: string, _count: number, value: string): Promise<number> {
    const list = this.lists.get(key);
    if (!list) return 0;
    let removed = 0;
    const filtered = list.filter((v) => {
      if (v === value) {
        removed++;
        return false;
      }
      return true;
    });
    this.lists.set(key, filtered);
    return removed;
  }

  async llen(key: string): Promise<number> {
    return this.lists.get(key)?.length ?? 0;
  }

  async sadd(key: string, value: string): Promise<number> {
    const set = this.sets.get(key) ?? new Set<string>();
    const had = set.has(value);
    set.add(value);
    this.sets.set(key, set);
    return had ? 0 : 1;
  }

  async srem(key: string, value: string): Promise<number> {
    const set = this.sets.get(key);
    if (!set) return 0;
    return set.delete(value) ? 1 : 0;
  }

  async sismember(key: string, value: string): Promise<number> {
    return this.sets.get(key)?.has(value) ? 1 : 0;
  }

  async set(key: string, value: string): Promise<unknown> {
    this.strings.set(key, value);
    return 'OK';
  }

  async get(key: string): Promise<string | null> {
    return this.strings.get(key) ?? null;
  }

  async del(key: string): Promise<number> {
    return this.strings.delete(key) ? 1 : 0;
  }

  async ping(): Promise<string> {
    return 'PONG';
  }

  async quit(): Promise<void> {
    this.lists.clear();
    this.sets.clear();
    this.strings.clear();
  }
}

function createRealClient(url: string): RedisLike {
  const client = new Redis(url, {
    maxRetriesPerRequest: 3,
    lazyConnect: false,
    retryStrategy: (times: number) => Math.min(times * 200, 2000),
  });
  client.on('error', (err: Error) => logger.error('Redis error', { error: err.message }));
  client.on('connect', () => logger.info('Redis connected'));
  // ioredis already satisfies RedisLike's shape for the methods we use.
  return client as unknown as RedisLike;
}

export const redis: RedisLike = usingRedisShim
  ? new InMemoryRedis()
  : createRealClient(env.REDIS_URL!);

if (usingRedisShim) {
  logger.warn('Using in-memory Redis shim (REDIS_URL not set). Dev only — not for production.');
}
