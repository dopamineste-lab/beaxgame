import { randomUUID } from 'node:crypto';
import { isPersistenceEnabled, query } from '../db/pool.js';
import { logger } from '../logger.js';
import { applyMove, createGame, isTerminal } from './engine.js';
import type { GameMode, GameState, Symbol } from './types.js';

/**
 * Owns the lifecycle of active matches. Game state is authoritative and held in
 * memory on the instance that created the match (single-instance model for this
 * slice — see docs/ARCHITECTURE.md "Scaling" for the Redis-adapter path to make
 * game state cluster-wide). Results are persisted to Postgres when enabled.
 */

export interface PlayerSlot {
  playerId: string;
  connected: boolean;
}

export interface Match {
  id: string;
  mode: GameMode;
  x: PlayerSlot;
  o: PlayerSlot;
  state: GameState;
  createdAt: number;
}

const matches = new Map<string, Match>();
/** Reverse index: playerId → matchId, for reconnect and duplicate detection. */
const playerToMatch = new Map<string, string>();

/** Create a match between two players. First arg becomes X (moves first). */
export async function createMatch(mode: GameMode, playerXId: string, playerOId: string): Promise<Match> {
  const match: Match = {
    id: randomUUID(),
    mode,
    x: { playerId: playerXId, connected: true },
    o: { playerId: playerOId, connected: true },
    state: createGame(mode),
    createdAt: Date.now(),
  };
  matches.set(match.id, match);
  playerToMatch.set(playerXId, match.id);
  playerToMatch.set(playerOId, match.id);

  if (isPersistenceEnabled()) {
    try {
      await query(
        `INSERT INTO matches (id, mode, player_x, player_o, status)
         VALUES ($1, $2, $3, $4, 'active')`,
        [match.id, mode, playerXId, playerOId],
      );
    } catch (err) {
      logger.error('Failed to persist match', { error: errMessage(err) });
    }
  }
  return match;
}

export function getMatch(matchId: string): Match | undefined {
  return matches.get(matchId);
}

export function getMatchForPlayer(playerId: string): Match | undefined {
  const id = playerToMatch.get(playerId);
  return id ? matches.get(id) : undefined;
}

/** The symbol a player controls in a match, or null if they're not in it. */
export function symbolFor(match: Match, playerId: string): Symbol | null {
  if (match.x.playerId === playerId) return 'X';
  if (match.o.playerId === playerId) return 'O';
  return null;
}

/**
 * Apply a player's move authoritatively. Throws if the player isn't in the match
 * or the move is illegal (engine enforces turn/occupancy/range/terminal rules).
 * Returns the new state; persists the move and, if terminal, the result.
 */
export async function applyPlayerMove(matchId: string, playerId: string, index: number): Promise<GameState> {
  const match = matches.get(matchId);
  if (!match) throw new Error('MATCH_NOT_FOUND');

  const symbol = symbolFor(match, playerId);
  if (!symbol) throw new Error('NOT_IN_MATCH');

  const next = applyMove(match.state, symbol, index); // may throw GameError
  const moveNumber = next.moveCount;
  match.state = next;

  if (isPersistenceEnabled()) {
    void persistMove(matchId, moveNumber, symbol, index);
    if (isTerminal(next)) void persistResult(match);
  }

  if (isTerminal(next)) endMatch(match.id, /*abandoned*/ false);
  return next;
}

/** Mark a player's connection status (for reconnect/opponent-left UX). */
export function setConnected(matchId: string, playerId: string, connected: boolean): void {
  const match = matches.get(matchId);
  if (!match) return;
  if (match.x.playerId === playerId) match.x.connected = connected;
  if (match.o.playerId === playerId) match.o.connected = connected;
}

/**
 * End a match: remove it from memory and the reverse index. When `abandoned` is
 * true and persistence is on, the match row is marked abandoned.
 */
export function endMatch(matchId: string, abandoned: boolean): void {
  const match = matches.get(matchId);
  if (!match) return;
  matches.delete(matchId);
  if (playerToMatch.get(match.x.playerId) === matchId) playerToMatch.delete(match.x.playerId);
  if (playerToMatch.get(match.o.playerId) === matchId) playerToMatch.delete(match.o.playerId);

  if (abandoned && isPersistenceEnabled()) {
    void query(
      `UPDATE matches SET status = 'abandoned', ended_at = now() WHERE id = $1 AND ended_at IS NULL`,
      [matchId],
    ).catch((err) => logger.error('Failed to mark match abandoned', { error: errMessage(err) }));
  }
}

export function activeMatchCount(): number {
  return matches.size;
}

// ─── persistence helpers ─────────────────────────────────────────────────────

async function persistMove(matchId: string, moveNumber: number, symbol: Symbol, index: number): Promise<void> {
  try {
    await query(
      `INSERT INTO match_moves (match_id, move_number, symbol, cell_index)
       VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING`,
      [matchId, moveNumber, symbol, index],
    );
  } catch (err) {
    logger.error('Failed to persist move', { error: errMessage(err) });
  }
}

async function persistResult(match: Match): Promise<void> {
  try {
    await query(
      `UPDATE matches SET status = $2, winner = $3, ended_at = now() WHERE id = $1`,
      [match.id, match.state.status, match.state.winner],
    );
  } catch (err) {
    logger.error('Failed to persist result', { error: errMessage(err) });
  }
}

function errMessage(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
