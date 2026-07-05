/** Player marks. X always moves first. */
export type Symbol = 'X' | 'O';

/** A board cell: a placed mark or empty. */
export type Cell = Symbol | null;

/** Immutable board — a flat array of `size * size` cells, row-major. */
export type Board = readonly Cell[];

/** Terminal or in-progress status of a match. */
export type GameStatus = 'active' | 'x_won' | 'o_won' | 'draw';

/** Game mode. Only `classic` is implemented in this slice; others are roadmap. */
export type GameMode = 'classic' | 'hard' | 'expert' | 'insane' | 'nightmare' | 'impossible';

/**
 * Immutable, serializable game state. This is the single source of truth the
 * authoritative server owns and broadcasts; clients render it and never mutate it.
 */
export interface GameState {
  readonly mode: GameMode;
  readonly size: number;
  readonly winLength: number;
  readonly board: Board;
  readonly turn: Symbol;
  readonly status: GameStatus;
  readonly winner: Symbol | null;
  /** Index of the most recent move, for highlight animations. */
  readonly lastMove: number | null;
  /** Winning line cell indices when a player has won, else null. */
  readonly winningLine: readonly number[] | null;
  readonly moveCount: number;
}

/** Error thrown when a move violates the rules. Carries a machine-readable code. */
export class GameError extends Error {
  constructor(
    public readonly code:
      | 'OUT_OF_RANGE'
      | 'CELL_TAKEN'
      | 'NOT_YOUR_TURN'
      | 'GAME_OVER',
    message: string,
  ) {
    super(message);
    this.name = 'GameError';
  }
}
