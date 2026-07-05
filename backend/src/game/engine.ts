import { GameError, type Board, type Cell, type GameMode, type GameState, type Symbol } from './types.js';

/**
 * Pure, deterministic, I/O-free game engine — the authoritative rules of OX Arena.
 *
 * Everything here is a pure function of its inputs: given the same state and move
 * it always produces the same result and never mutates its arguments. That makes
 * it trivially unit-testable and safe to run identically on server (authoritative)
 * and, later, on the client (prediction). The socket/persistence layers wrap it.
 *
 * The engine is written for an N×N board with a configurable win length so the
 * advanced game modes (larger boards, k-in-a-row) reuse it unchanged. `classic`
 * is the standard 3×3, 3-in-a-row game.
 */

interface ModeConfig {
  readonly size: number;
  readonly winLength: number;
}

const MODE_CONFIG: Record<GameMode, ModeConfig> = {
  classic: { size: 3, winLength: 3 },
  // Roadmap modes reuse the same engine with different geometry / added mechanics.
  hard: { size: 4, winLength: 4 },
  expert: { size: 5, winLength: 4 },
  insane: { size: 5, winLength: 5 },
  nightmare: { size: 6, winLength: 5 },
  impossible: { size: 7, winLength: 5 },
};

/** Cache of winning-line index sets, keyed by `${size}:${winLength}`. */
const winningLineCache = new Map<string, readonly number[][]>();

/**
 * Generate every winning line (as arrays of board indices) for an N×N board
 * requiring `winLength` in a row — horizontal, vertical, and both diagonals.
 */
export function winningLines(size: number, winLength: number): readonly number[][] {
  const key = `${size}:${winLength}`;
  const cached = winningLineCache.get(key);
  if (cached) return cached;

  const lines: number[][] = [];
  const idx = (r: number, c: number): number => r * size + c;
  // Direction vectors: right, down, down-right, down-left.
  const directions = [
    [0, 1],
    [1, 0],
    [1, 1],
    [1, -1],
  ] as const;

  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      for (const [dr, dc] of directions) {
        const endR = r + dr * (winLength - 1);
        const endC = c + dc * (winLength - 1);
        if (endR < 0 || endR >= size || endC < 0 || endC >= size) continue;
        const line: number[] = [];
        for (let k = 0; k < winLength; k++) line.push(idx(r + dr * k, c + dc * k));
        lines.push(line);
      }
    }
  }

  winningLineCache.set(key, lines);
  return lines;
}

/** Create a fresh game state for the given mode. X always starts. */
export function createGame(mode: GameMode = 'classic'): GameState {
  const config = MODE_CONFIG[mode];
  const board: Cell[] = new Array<Cell>(config.size * config.size).fill(null);
  return {
    mode,
    size: config.size,
    winLength: config.winLength,
    board,
    turn: 'X',
    status: 'active',
    winner: null,
    lastMove: null,
    winningLine: null,
    moveCount: 0,
  };
}

/** The opposite symbol. */
export function opponentOf(symbol: Symbol): Symbol {
  return symbol === 'X' ? 'O' : 'X';
}

/**
 * Evaluate a board for a winner. Returns the winning symbol and its line, or
 * `null` if there is no winner yet.
 */
export function evaluateWinner(
  board: Board,
  size: number,
  winLength: number,
): { symbol: Symbol; line: readonly number[] } | null {
  for (const line of winningLines(size, winLength)) {
    const first = board[line[0]!];
    // `== null` rules out both null (empty cell) and undefined (defensive, from
    // noUncheckedIndexedAccess), narrowing `first` to a concrete Symbol.
    if (first == null) continue;
    if (line.every((i) => board[i] === first)) {
      return { symbol: first, line };
    }
  }
  return null;
}

/**
 * Apply `symbol`'s move at `index` to `state`, returning a NEW state.
 * Throws {@link GameError} if the move is illegal — the authoritative server
 * relies on this to reject invalid or out-of-turn moves from clients.
 */
export function applyMove(state: GameState, symbol: Symbol, index: number): GameState {
  if (state.status !== 'active') {
    throw new GameError('GAME_OVER', 'The game is already over.');
  }
  if (symbol !== state.turn) {
    throw new GameError('NOT_YOUR_TURN', `It is ${state.turn}'s turn, not ${symbol}'s.`);
  }
  if (!Number.isInteger(index) || index < 0 || index >= state.board.length) {
    throw new GameError('OUT_OF_RANGE', `Move index ${index} is outside the board.`);
  }
  if (state.board[index] !== null) {
    throw new GameError('CELL_TAKEN', `Cell ${index} is already occupied.`);
  }

  const board = state.board.slice();
  board[index] = symbol;
  const moveCount = state.moveCount + 1;

  const win = evaluateWinner(board, state.size, state.winLength);
  if (win) {
    return {
      ...state,
      board,
      status: win.symbol === 'X' ? 'x_won' : 'o_won',
      winner: win.symbol,
      lastMove: index,
      winningLine: win.line,
      moveCount,
    };
  }

  if (moveCount === board.length) {
    return { ...state, board, status: 'draw', winner: null, lastMove: index, moveCount };
  }

  return {
    ...state,
    board,
    turn: opponentOf(symbol),
    lastMove: index,
    moveCount,
  };
}

/** Convenience: is this state terminal (won or drawn)? */
export function isTerminal(state: GameState): boolean {
  return state.status !== 'active';
}
