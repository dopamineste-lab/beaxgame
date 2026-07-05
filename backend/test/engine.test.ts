import { describe, expect, it } from 'vitest';
import {
  applyMove,
  createGame,
  evaluateWinner,
  opponentOf,
  winningLines,
} from '../src/game/engine.js';
import { GameError, type GameState } from '../src/game/types.js';

/** Play a sequence of indices, alternating starting with X. Returns final state. */
function play(indices: number[], mode: Parameters<typeof createGame>[0] = 'classic'): GameState {
  let state = createGame(mode);
  for (const index of indices) {
    state = applyMove(state, state.turn, index);
  }
  return state;
}

describe('createGame', () => {
  it('creates an empty active board with X to move', () => {
    const g = createGame('classic');
    expect(g.board).toHaveLength(9);
    expect(g.board.every((c) => c === null)).toBe(true);
    expect(g.turn).toBe('X');
    expect(g.status).toBe('active');
    expect(g.winner).toBeNull();
  });
});

describe('opponentOf', () => {
  it('flips symbols', () => {
    expect(opponentOf('X')).toBe('O');
    expect(opponentOf('O')).toBe('X');
  });
});

describe('winningLines', () => {
  it('produces 8 lines for classic 3x3', () => {
    expect(winningLines(3, 3)).toHaveLength(8); // 3 rows + 3 cols + 2 diagonals
  });

  it('produces the correct count for a 4x4 board needing 4-in-a-row', () => {
    // 4 rows + 4 cols + 2 diagonals = 10
    expect(winningLines(4, 4)).toHaveLength(10);
  });
});

describe('applyMove — legality', () => {
  it('rejects a move on an occupied cell', () => {
    let g = createGame();
    g = applyMove(g, 'X', 4);
    expect(() => applyMove(g, 'O', 4)).toThrowError(GameError);
    try {
      applyMove(g, 'O', 4);
    } catch (e) {
      expect((e as GameError).code).toBe('CELL_TAKEN');
    }
  });

  it('rejects an out-of-turn move', () => {
    const g = createGame(); // X to move
    try {
      applyMove(g, 'O', 0);
      throw new Error('should have thrown');
    } catch (e) {
      expect((e as GameError).code).toBe('NOT_YOUR_TURN');
    }
  });

  it('rejects out-of-range indices', () => {
    const g = createGame();
    for (const bad of [-1, 9, 100, 1.5]) {
      try {
        applyMove(g, 'X', bad);
        throw new Error('should have thrown');
      } catch (e) {
        expect((e as GameError).code).toBe('OUT_OF_RANGE');
      }
    }
  });

  it('rejects any move after the game is over', () => {
    // X wins on the top row: X 0,1,2 ; O 3,4
    const g = play([0, 3, 1, 4, 2]);
    expect(g.status).toBe('x_won');
    try {
      applyMove(g, 'O', 5);
      throw new Error('should have thrown');
    } catch (e) {
      expect((e as GameError).code).toBe('GAME_OVER');
    }
  });
});

describe('applyMove — immutability', () => {
  it('does not mutate the previous state', () => {
    const g0 = createGame();
    const g1 = applyMove(g0, 'X', 0);
    expect(g0.board[0]).toBeNull();
    expect(g1.board[0]).toBe('X');
    expect(g0).not.toBe(g1);
  });
});

describe('win / draw detection', () => {
  it('detects a row win with the winning line', () => {
    const g = play([0, 3, 1, 4, 2]); // X: 0,1,2
    expect(g.status).toBe('x_won');
    expect(g.winner).toBe('X');
    expect(g.winningLine).toEqual([0, 1, 2]);
  });

  it('detects a column win for O', () => {
    // X:0, O:1, X:4, O:? build O column 1,4,7? use indices col 2 -> 2,5,8
    // X: 0, O:2, X:3, O:5, X:7, O:8  -> O column 2,5,8
    const g = play([0, 2, 3, 5, 7, 8]);
    expect(g.status).toBe('o_won');
    expect(g.winner).toBe('O');
    expect(g.winningLine).toEqual([2, 5, 8]);
  });

  it('detects a diagonal win', () => {
    const g = play([0, 1, 4, 2, 8]); // X: 0,4,8 diagonal
    expect(g.winner).toBe('X');
    expect(g.winningLine).toEqual([0, 4, 8]);
  });

  it('detects a draw with a full board and no winner', () => {
    // A classic drawn game:
    // X O X
    // X X O
    // O X O
    const g = play([0, 1, 2, 5, 3, 6, 4, 8, 7]);
    expect(g.status).toBe('draw');
    expect(g.winner).toBeNull();
    expect(g.board.every((c) => c !== null)).toBe(true);
  });

  it('tracks move count and last move', () => {
    let g = createGame();
    g = applyMove(g, 'X', 4);
    expect(g.moveCount).toBe(1);
    expect(g.lastMove).toBe(4);
    g = applyMove(g, 'O', 0);
    expect(g.moveCount).toBe(2);
    expect(g.lastMove).toBe(0);
    expect(g.turn).toBe('X');
  });
});

describe('evaluateWinner (standalone)', () => {
  it('returns null on an empty board', () => {
    expect(evaluateWinner(createGame().board, 3, 3)).toBeNull();
  });
});
