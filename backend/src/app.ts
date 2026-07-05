import Fastify, { type FastifyInstance } from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import { Server as SocketIOServer } from 'socket.io';
import { env } from './config/env.js';
import { issueSession } from './auth/session.js';
import { registerHealthRoutes } from './routes/health.js';
import { registerSocketHandlers } from './socket/handlers.js';
import type {
  ClientToServerEvents,
  ServerToClientEvents,
  SocketData,
} from './socket/protocol.js';

export interface BuiltServer {
  app: FastifyInstance;
  io: SocketIOServer<ClientToServerEvents, ServerToClientEvents, Record<string, never>, SocketData>;
}

/**
 * Assemble the HTTP (Fastify) and realtime (Socket.IO) servers, sharing one HTTP
 * listener. Secure headers via helmet, permissive CORS suitable for a public game
 * API, a REST session-bootstrap endpoint, and health checks.
 */
export async function buildServer(): Promise<BuiltServer> {
  const app = Fastify({ logger: false, trustProxy: true });

  await app.register(helmet, {
    // The API serves JSON/WS only; relax CSP which would otherwise block nothing useful here.
    contentSecurityPolicy: false,
    crossOriginResourcePolicy: { policy: 'cross-origin' },
  });

  const origins = env.CORS_ORIGIN === '*' ? true : env.CORS_ORIGIN.split(',').map((o) => o.trim());
  await app.register(cors, { origin: origins, methods: ['GET', 'POST'] });

  await registerHealthRoutes(app);

  // REST bootstrap: clients that prefer to obtain a token before opening the
  // socket can POST here. (The socket handshake also mints one automatically.)
  app.post('/api/session', async () => {
    const session = await issueSession();
    return { token: session.token, playerId: session.playerId, expiresAt: session.expiresAt };
  });

  app.get('/', async () => ({ name: 'OX Arena API', status: 'ok' }));

  // Attach Socket.IO to Fastify's underlying HTTP server.
  const io = new SocketIOServer<
    ClientToServerEvents,
    ServerToClientEvents,
    Record<string, never>,
    SocketData
  >(app.server, {
    cors: { origin: origins, methods: ['GET', 'POST'] },
    // Tunable transports; websocket preferred, polling fallback for restrictive networks.
    transports: ['websocket', 'polling'],
    pingInterval: 20_000,
    pingTimeout: 20_000,
  });

  registerSocketHandlers(io);

  return { app, io };
}
