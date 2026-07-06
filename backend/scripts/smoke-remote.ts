/**
 * Remote smoke test — plays a full authoritative match against a DEPLOYED server.
 *
 * Usage: SMOKE_URL=https://ox-arena-api.onrender.com npm run smoke:remote
 *
 * Connects two real Socket.IO clients to the given URL, waits for matchmaking to
 * pair them, plays a scripted game to a win, and asserts both clients observe the
 * same authoritative result. Exits non-zero on failure.
 */
import { io as connect, type Socket } from 'socket.io-client';

const URL = process.env.SMOKE_URL ?? 'http://localhost:8080';
const TIMEOUT_MS = 30_000;

const SEQUENCE: { symbol: 'X' | 'O'; index: number }[] = [
  { symbol: 'X', index: 0 },
  { symbol: 'O', index: 3 },
  { symbol: 'X', index: 1 },
  { symbol: 'O', index: 4 },
  { symbol: 'X', index: 2 }, // X wins the top row
];

function fail(msg: string): never {
  console.error(`\n❌ REMOTE SMOKE FAILED: ${msg}`);
  process.exit(1);
}

function driveClient(label: string, onOver: (winner: string | null) => void): Socket {
  const socket = connect(URL, { transports: ['websocket'] });
  let mySymbol: 'X' | 'O' | null = null;
  let matchId: string | null = null;

  const maybeMove = (turn: 'X' | 'O', moveCount: number): void => {
    if (!mySymbol || !matchId) return;
    const next = SEQUENCE[moveCount];
    if (next && next.symbol === mySymbol && turn === mySymbol) {
      socket.emit('game:move', { matchId, index: next.index });
    }
  };

  socket.on('connect', () => {
    console.log(`· ${label} connected to ${URL}`);
    socket.emit('queue:join', { mode: 'classic' });
  });
  socket.on('session', (p: { playerId: string }) =>
    console.log(`· ${label} session ${p.playerId.slice(0, 8)}`),
  );
  socket.on('queue:searching', () => console.log(`· ${label} searching…`));
  socket.on('match:found', (p: { matchId: string; yourSymbol: 'X' | 'O'; state: { turn: 'X' | 'O'; moveCount: number } }) => {
    mySymbol = p.yourSymbol;
    matchId = p.matchId;
    console.log(`· ${label} matched as ${p.yourSymbol} (${p.matchId.slice(0, 8)})`);
    maybeMove(p.state.turn, p.state.moveCount);
  });
  socket.on('game:state', (s: { turn: 'X' | 'O'; moveCount: number; status: string }) => {
    if (s.status === 'active') maybeMove(s.turn, s.moveCount);
  });
  socket.on('game:over', (o: { winner: string | null; status: string }) => {
    console.log(`· ${label} game:over → ${o.status} (winner ${o.winner})`);
    onOver(o.winner);
  });
  socket.on('connect_error', (e: Error) => console.log(`· ${label} connect_error: ${e.message}`));

  return socket;
}

async function main(): Promise<void> {
  console.log(`▶ remote smoke against ${URL}\n`);
  let overCount = 0;
  const winners: (string | null)[] = [];
  const sockets: Socket[] = [];

  const done = new Promise<void>((resolve) => {
    const onOver = (w: string | null): void => {
      winners.push(w);
      if (++overCount === 2) resolve();
    };
    sockets.push(driveClient('P1', onOver), driveClient('P2', onOver));
  });

  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error('timed out — check queue pairing on the server')), TIMEOUT_MS),
  );

  try {
    await Promise.race([done, timeout]);
  } catch (e) {
    fail((e as Error).message);
  }

  if (!winners.every((w) => w === 'X')) fail(`expected winner X on both clients, got ${JSON.stringify(winners)}`);
  console.log('\n✅ REMOTE SMOKE PASSED: full authoritative match completed on the deployed server.');
  sockets.forEach((s) => s.disconnect());
  process.exit(0);
}

main().catch((e) => fail(e instanceof Error ? e.message : String(e)));
