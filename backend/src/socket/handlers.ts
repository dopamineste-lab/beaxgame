import type { Server, Socket } from 'socket.io';
import { z } from 'zod';
import { env } from '../config/env.js';
import { GameError, type GameMode } from '../game/types.js';
import {
  applyPlayerMove,
  createMatch,
  endMatch,
  getMatchForPlayer,
  setConnected,
  symbolFor,
  type Match,
} from '../game/gameManager.js';
import { isQueued, joinQueue, leaveAllQueues, leaveQueue } from '../matchmaking/queue.js';
import { issueSession, signToken, verifySession } from '../auth/session.js';
import { logger } from '../logger.js';
import {
  serializeState,
  type ClientToServerEvents,
  type ServerToClientEvents,
  type SocketData,
} from './protocol.js';
import { TokenBucket } from './rateLimit.js';

type AppServer = Server<ClientToServerEvents, ServerToClientEvents, Record<string, never>, SocketData>;
type AppSocket = Socket<ClientToServerEvents, ServerToClientEvents, Record<string, never>, SocketData>;

const SUPPORTED_MODES: GameMode[] = ['classic'];
const COUNTDOWN_MS = 3000;

// ─── Inbound payload validation (zod) ────────────────────────────────────────
// Every client-supplied payload is schema-validated before any handler logic
// runs; anything malformed is rejected with BAD_PAYLOAD.
const joinSchema = z
  .object({ mode: z.enum(['classic', 'hard', 'expert', 'insane', 'nightmare', 'impossible']).optional() })
  .nullish();
const moveSchema = z.object({
  matchId: z.string().uuid(),
  index: z.number().int().min(0).max(64), // upper bound covers the largest roadmap board (8x8)
});
const leaveSchema = z.object({ matchId: z.string().uuid() });

/** playerId → their current live socket (one connection per identity). */
const socketsByPlayer = new Map<string, AppSocket>();
/** playerId → queue-timeout timer. */
const queueTimers = new Map<string, NodeJS.Timeout>();
/** playerId → reconnect-grace timer (started when a player in a match drops). */
const graceTimers = new Map<string, NodeJS.Timeout>();

const roomOf = (matchId: string): string => `match:${matchId}`;

export function registerSocketHandlers(io: AppServer): void {
  // Authenticate during the handshake. A missing/invalid token mints a fresh
  // anonymous identity, so a brand-new client can connect with zero setup.
  io.use(async (socket, next) => {
    const token = socket.handshake.auth?.token as string | undefined;
    const existing = await verifySession(token);
    if (existing) {
      socket.data.playerId = existing;
    } else {
      const session = await issueSession();
      socket.data.playerId = session.playerId;
      // Stash the freshly minted token so we can hand it back after connect.
      (socket.data as SocketData & { mintedToken?: string }).mintedToken = session.token;
    }
    next();
  });

  io.on('connection', (socket) => {
    const playerId = socket.data.playerId;

    // Enforce a single live connection per identity (duplicate/idle cleanup).
    const prior = socketsByPlayer.get(playerId);
    if (prior && prior.id !== socket.id) {
      prior.emit('error', { code: 'REPLACED', message: 'Connected from another session.' });
      prior.disconnect(true);
    }
    socketsByPlayer.set(playerId, socket);

    const minted = (socket.data as SocketData & { mintedToken?: string }).mintedToken;
    socket.emit('session', { playerId, token: minted ?? signToken(playerId) });
    logger.debug('Socket connected', { playerId, socket: socket.id });

    // If the player was mid-match, resume it (reconnect path).
    const resume = getMatchForPlayer(playerId);
    if (resume) resumeMatch(socket, resume, playerId);

    // Per-connection rate limit: bursts up to 20 events, sustained 10/s. A client
    // that keeps flooding long past the limit is disconnected outright.
    const bucket = new TokenBucket(20, 10);
    const guarded = <T>(handler: (payload?: T) => void) => (payload?: T): void => {
      if (!bucket.take()) {
        if (bucket.abusive) {
          logger.warn('Disconnecting abusive client', { playerId });
          socket.disconnect(true);
          return;
        }
        socket.emit('error', { code: 'RATE_LIMITED', message: 'Slow down.' });
        return;
      }
      handler(payload);
    };

    socket.on('queue:join', guarded((payload) => {
      const parsed = joinSchema.safeParse(payload);
      if (!parsed.success) {
        socket.emit('error', { code: 'BAD_PAYLOAD', message: 'Invalid queue payload.' });
        return;
      }
      void handleJoin(socket, playerId, parsed.data?.mode);
    }));
    socket.on('queue:cancel', guarded(() => void handleCancel(socket, playerId)));
    socket.on('game:move', guarded((payload) => {
      const parsed = moveSchema.safeParse(payload);
      if (!parsed.success) {
        socket.emit('error', { code: 'BAD_PAYLOAD', message: 'Invalid move payload.' });
        return;
      }
      void handleMove(io, socket, playerId, parsed.data);
    }));
    socket.on('game:leave', guarded((payload) => {
      const parsed = leaveSchema.safeParse(payload);
      if (!parsed.success) {
        socket.emit('error', { code: 'BAD_PAYLOAD', message: 'Invalid leave payload.' });
        return;
      }
      handleLeave(io, playerId, parsed.data.matchId);
    }));
    socket.on('disconnect', () => void handleDisconnect(socket, playerId));
  });
}

async function handleJoin(
  socket: AppSocket,
  playerId: string,
  requestedMode: GameMode | undefined,
): Promise<void> {
  const mode = SUPPORTED_MODES.includes(requestedMode as GameMode)
    ? (requestedMode as GameMode)
    : 'classic';

  // Already in a match? Re-send its state rather than queueing again.
  const current = getMatchForPlayer(playerId);
  if (current) {
    resumeMatch(socket, current, playerId);
    return;
  }

  const result = await joinQueue(playerId, mode);
  if (result.status === 'already_queued') {
    socket.emit('queue:searching', { since: Date.now(), timeoutMs: env.QUEUE_TIMEOUT_MS });
    return;
  }

  if (result.status === 'queued') {
    socket.emit('queue:searching', { since: Date.now(), timeoutMs: env.QUEUE_TIMEOUT_MS });
    const timer = setTimeout(() => {
      void leaveQueue(playerId, mode);
      queueTimers.delete(playerId);
      socketsByPlayer.get(playerId)?.emit('queue:cancelled');
    }, env.QUEUE_TIMEOUT_MS);
    queueTimers.set(playerId, timer);
    return;
  }

  // Matched — build the match and notify both players.
  clearQueueTimer(result.xId);
  clearQueueTimer(result.oId);
  const match = await createMatch(mode, result.xId, result.oId);
  const startAt = Date.now() + COUNTDOWN_MS;

  for (const slot of [match.x, match.o] as const) {
    const s = socketsByPlayer.get(slot.playerId);
    if (!s) continue;
    void s.join(roomOf(match.id));
    const symbol = symbolFor(match, slot.playerId)!;
    const opponentId = symbol === 'X' ? match.o.playerId : match.x.playerId;
    s.emit('match:found', {
      matchId: match.id,
      mode: match.mode,
      yourSymbol: symbol,
      opponentId,
      startAt,
      state: serializeState(match.id, match.state),
    });
  }
  logger.info('Match created', { matchId: match.id, mode, x: match.x.playerId, o: match.o.playerId });
}

async function handleCancel(socket: AppSocket, playerId: string): Promise<void> {
  clearQueueTimer(playerId);
  if (await isQueued(playerId)) {
    await leaveAllQueues(playerId, SUPPORTED_MODES);
  }
  socket.emit('queue:cancelled');
}

async function handleMove(
  io: AppServer,
  socket: AppSocket,
  playerId: string,
  payload: { matchId: string; index: number },
): Promise<void> {
  if (!payload || typeof payload.matchId !== 'string' || !Number.isInteger(payload.index)) {
    socket.emit('error', { code: 'BAD_PAYLOAD', message: 'Invalid move payload.' });
    return;
  }
  try {
    const state = await applyPlayerMove(payload.matchId, playerId, payload.index);
    const stateMsg = serializeState(payload.matchId, state);
    io.to(roomOf(payload.matchId)).emit('game:state', stateMsg);
    if (state.status !== 'active') {
      io.to(roomOf(payload.matchId)).emit('game:over', {
        matchId: payload.matchId,
        status: state.status,
        winner: state.winner,
      });
    }
  } catch (err) {
    if (err instanceof GameError) {
      socket.emit('error', { code: err.code, message: err.message });
    } else {
      const code = err instanceof Error ? err.message : 'MOVE_FAILED';
      socket.emit('error', { code, message: 'Move rejected.' });
    }
  }
}

function handleLeave(io: AppServer, playerId: string, matchId: string | undefined): void {
  const match = matchId ? getMatchForPlayer(playerId) : undefined;
  if (!match || match.id !== matchId) return;
  const opponentId = match.x.playerId === playerId ? match.o.playerId : match.x.playerId;
  io.to(roomOf(match.id)).emit('opponent:left', { matchId: match.id });
  endMatch(match.id, /*abandoned*/ true);
  socketsByPlayer.get(opponentId)?.leave(roomOf(match.id));
}

async function handleDisconnect(socket: AppSocket, playerId: string): Promise<void> {
  // Only clear the registry entry if this socket is still the active one.
  if (socketsByPlayer.get(playerId)?.id === socket.id) {
    socketsByPlayer.delete(playerId);
  }
  clearQueueTimer(playerId);
  await leaveAllQueues(playerId, SUPPORTED_MODES);

  const match = getMatchForPlayer(playerId);
  if (!match) return;

  setConnected(match.id, playerId, false);
  const opponentId = match.x.playerId === playerId ? match.o.playerId : match.x.playerId;

  // Give the player a grace window to reconnect. Only if it expires do we
  // abandon the match and tell the opponent they won — notifying immediately
  // would flash a premature victory screen for what may be a 2-second blip.
  const timer = setTimeout(() => {
    graceTimers.delete(playerId);
    const still = getMatchForPlayer(playerId);
    if (still && still.id === match.id) {
      socketsByPlayer.get(opponentId)?.emit('opponent:left', { matchId: match.id });
      endMatch(match.id, /*abandoned*/ true);
      socketsByPlayer.get(opponentId)?.leave(roomOf(match.id));
    }
  }, env.RECONNECT_GRACE_MS);
  graceTimers.set(playerId, timer);
  logger.debug('Player disconnected mid-match', { playerId, matchId: match.id });
}

/** Re-attach a reconnecting player to their match and push current state. */
function resumeMatch(socket: AppSocket, match: Match, playerId: string): void {
  const grace = graceTimers.get(playerId);
  if (grace) {
    clearTimeout(grace);
    graceTimers.delete(playerId);
  }
  setConnected(match.id, playerId, true);
  void socket.join(roomOf(match.id));

  const symbol = symbolFor(match, playerId)!;
  const opponentId = symbol === 'X' ? match.o.playerId : match.x.playerId;
  socket.emit('match:found', {
    matchId: match.id,
    mode: match.mode,
    yourSymbol: symbol,
    opponentId,
    startAt: Date.now(), // already started — no countdown on resume
    state: serializeState(match.id, match.state),
  });
  socketsByPlayer.get(opponentId)?.emit('opponent:reconnected', { matchId: match.id });
}

function clearQueueTimer(playerId: string): void {
  const t = queueTimers.get(playerId);
  if (t) {
    clearTimeout(t);
    queueTimers.delete(playerId);
  }
}
