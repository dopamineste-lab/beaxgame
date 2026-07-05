import { randomUUID } from 'node:crypto';
import jwt from 'jsonwebtoken';
import { env } from '../config/env.js';
import { isPersistenceEnabled, query } from '../db/pool.js';
import { logger } from '../logger.js';

/**
 * Anonymous session management.
 *
 * There is no login. On first contact a player receives a random UUID identity
 * and a signed JWT bearing it. The token is the only credential; it is short-ish
 * lived (default 7 days) and can be rotated by minting a new one. When Postgres
 * is configured the player and session rows are persisted; otherwise identity is
 * ephemeral (fine for dev / stateless play).
 */

export interface Session {
  token: string;
  playerId: string;
  expiresAt: number; // epoch ms
}

interface TokenPayload {
  sub: string; // playerId
  typ: 'anon';
}

/** Mint a brand-new anonymous player + session. */
export async function issueSession(): Promise<Session> {
  const playerId = randomUUID();
  const expiresAt = Date.now() + env.JWT_TTL_SECONDS * 1000;

  if (isPersistenceEnabled()) {
    try {
      await query('INSERT INTO players (id) VALUES ($1) ON CONFLICT DO NOTHING', [playerId]);
      await query(
        'INSERT INTO sessions (player_id, expires_at) VALUES ($1, to_timestamp($2))',
        [playerId, Math.floor(expiresAt / 1000)],
      );
    } catch (err) {
      // Persistence failure must not block play — log and continue with the token.
      logger.error('Failed to persist new session', {
        error: err instanceof Error ? err.message : String(err),
      });
    }
  }

  const token = signToken(playerId);
  return { token, playerId, expiresAt };
}

/** Sign a token for an already-known player id (used for rotation / reconnect). */
export function signToken(playerId: string): string {
  const payload: TokenPayload = { sub: playerId, typ: 'anon' };
  return jwt.sign(payload, env.JWT_SECRET, { expiresIn: env.JWT_TTL_SECONDS });
}

/**
 * Verify a token and return the player id, or `null` if invalid/expired.
 * Also touches `last_seen_at` when persistence is enabled.
 */
export async function verifySession(token: string | undefined): Promise<string | null> {
  if (!token) return null;
  try {
    const decoded = jwt.verify(token, env.JWT_SECRET) as TokenPayload;
    if (decoded.typ !== 'anon' || typeof decoded.sub !== 'string') return null;
    return decoded.sub;
  } catch {
    return null;
  }
}
