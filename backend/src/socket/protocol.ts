import type { GameMode, GameState, Symbol } from '../game/types.js';

/**
 * The wire protocol shared by server and client. Event names and payload shapes
 * are documented in `docs/API.md`; keep the two in sync.
 *
 * Convention: `namespace:action`. Client→server events are commands; server→client
 * events are facts (the server is authoritative — clients render what they're told).
 */

// ─── Client → Server ─────────────────────────────────────────────────────────
export interface ClientToServerEvents {
  'queue:join': (payload: { mode?: GameMode }) => void;
  'queue:cancel': () => void;
  'game:move': (payload: { matchId: string; index: number }) => void;
  'game:leave': (payload: { matchId: string }) => void;
}

// ─── Server → Client ─────────────────────────────────────────────────────────
export interface ServerToClientEvents {
  /** Sent once after a successful handshake; echoes the (possibly freshly minted) identity. */
  session: (payload: { playerId: string; token?: string }) => void;
  'queue:searching': (payload: { since: number; timeoutMs: number }) => void;
  'queue:cancelled': () => void;
  'match:found': (payload: MatchFoundPayload) => void;
  'game:state': (payload: GameStatePayload) => void;
  'game:over': (payload: GameOverPayload) => void;
  'opponent:left': (payload: { matchId: string }) => void;
  'opponent:reconnected': (payload: { matchId: string }) => void;
  error: (payload: { code: string; message: string }) => void;
}

export interface SocketData {
  playerId: string;
}

export interface MatchFoundPayload {
  matchId: string;
  mode: GameMode;
  yourSymbol: Symbol;
  opponentId: string;
  /** Wall-clock epoch ms when moves become legal (cosmetic countdown target). */
  startAt: number;
  state: GameStatePayload;
}

/** Serialized game state as sent to clients. Mirrors {@link GameState}. */
export interface GameStatePayload {
  matchId: string;
  mode: GameMode;
  size: number;
  board: (Symbol | null)[];
  turn: Symbol;
  status: GameState['status'];
  winner: Symbol | null;
  lastMove: number | null;
  winningLine: number[] | null;
  moveCount: number;
}

export interface GameOverPayload {
  matchId: string;
  status: GameState['status'];
  winner: Symbol | null;
}

export function serializeState(matchId: string, state: GameState): GameStatePayload {
  return {
    matchId,
    mode: state.mode,
    size: state.size,
    board: [...state.board],
    turn: state.turn,
    status: state.status,
    winner: state.winner,
    lastMove: state.lastMove,
    winningLine: state.winningLine ? [...state.winningLine] : null,
    moveCount: state.moveCount,
  };
}
