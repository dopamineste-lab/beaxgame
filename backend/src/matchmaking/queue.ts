import { redis } from '../redis/client.js';
import type { GameMode } from '../game/types.js';

/**
 * Redis-backed matchmaking queue.
 *
 * Design: the queue itself lives in Redis (a list per mode + a presence set for
 * duplicate-connection prevention), so it survives instance restarts and is
 * observable. Pairing decisions are serialized through an in-process async mutex,
 * which is race-free for a single instance. For multi-instance matchmaking the
 * same structure is popped under a Redis distributed lock (Lua `LPOP`+publish) —
 * see docs/ARCHITECTURE.md "Scaling". The socket layer reacts to a `matched`
 * result by notifying both players, so no pub/sub is needed while single-instance.
 */

export type MatchmakeResult =
  | { status: 'queued' }
  | { status: 'already_queued' }
  | { status: 'matched'; xId: string; oId: string; mode: GameMode };

const PRESENCE_KEY = 'mm:presence';
const queueKey = (mode: GameMode): string => `mm:queue:${mode}`;

/** Minimal FIFO async mutex — serializes matchmaking critical sections. */
class Mutex {
  private tail: Promise<void> = Promise.resolve();
  run<T>(fn: () => Promise<T>): Promise<T> {
    const result = this.tail.then(fn);
    // Keep the chain alive regardless of individual outcomes.
    this.tail = result.then(
      () => undefined,
      () => undefined,
    );
    return result;
  }
}

const mutex = new Mutex();

/** Randomly assign who plays X (moves first) for fairness. */
function assignSymbols(a: string, b: string): [string, string] {
  return Math.random() < 0.5 ? [a, b] : [b, a];
}

/**
 * Attempt to join the queue for `mode`. If an opponent is already waiting, the
 * two are paired immediately and removed from the queue; otherwise the player is
 * enqueued and must wait for a future joiner.
 */
export async function joinQueue(playerId: string, mode: GameMode): Promise<MatchmakeResult> {
  return mutex.run(async () => {
    if ((await redis.sismember(PRESENCE_KEY, playerId)) === 1) {
      return { status: 'already_queued' };
    }

    const key = queueKey(mode);
    const waiting = await redis.lrange(key, 0, -1);
    const opponentId = waiting.find((id) => id !== playerId);

    if (opponentId) {
      await redis.lrem(key, 0, opponentId);
      await redis.srem(PRESENCE_KEY, opponentId);
      const [xId, oId] = assignSymbols(playerId, opponentId);
      return { status: 'matched', xId, oId, mode };
    }

    await redis.rpush(key, playerId);
    await redis.sadd(PRESENCE_KEY, playerId);
    return { status: 'queued' };
  });
}

/** Remove a player from any queue they're in (cancel / disconnect cleanup). */
export async function leaveQueue(playerId: string, mode: GameMode): Promise<void> {
  await mutex.run(async () => {
    await redis.lrem(queueKey(mode), 0, playerId);
    await redis.srem(PRESENCE_KEY, playerId);
  });
}

/** Remove a player from every mode's queue (used on disconnect when mode unknown). */
export async function leaveAllQueues(playerId: string, modes: GameMode[]): Promise<void> {
  await mutex.run(async () => {
    for (const mode of modes) {
      await redis.lrem(queueKey(mode), 0, playerId);
    }
    await redis.srem(PRESENCE_KEY, playerId);
  });
}

export async function isQueued(playerId: string): Promise<boolean> {
  return (await redis.sismember(PRESENCE_KEY, playerId)) === 1;
}

export async function queueDepth(mode: GameMode): Promise<number> {
  return redis.llen(queueKey(mode));
}
