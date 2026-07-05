# OX Arena — Architecture

## Overview

OX Arena is a real-time multiplayer game with an **authoritative server**: the
backend owns all game state and validates every action; clients render state and
send intents. This prevents cheating and keeps two devices perfectly in sync.

```mermaid
flowchart LR
    subgraph Android["Android client (Kotlin / Compose)"]
        UI[UI screens] --> VM[GameFlowViewModel]
        VM --> MC[MultiplayerClient]
        MC --> SIO[Socket.IO client]
    end
    subgraph Render["Backend (Render.com)"]
        API[Fastify HTTP] ---|same port| WS[Socket.IO]
        WS --> H[Socket handlers]
        H --> Q[Matchmaking queue]
        H --> GM[Game manager]
        GM --> EN[Game engine pure]
        Q --> R[(Redis)]
        H --> R
        GM --> PG[(PostgreSQL)]
        API --> PG
    end
    SIO <-->|WSS| WS
```

## Backend layers

| Layer | Responsibility | Key files |
|-------|----------------|-----------|
| Transport | HTTP + WebSocket, CORS, secure headers | `src/app.ts`, `src/index.ts` |
| Auth | Anonymous JWT sessions | `src/auth/session.ts` |
| Matchmaking | Redis-backed queue, pairing, duplicate prevention | `src/matchmaking/queue.ts` |
| Game orchestration | Match lifecycle, persistence, reconnect | `src/game/gameManager.ts`, `src/socket/handlers.ts` |
| Rules (pure) | Move validation, win/draw — no I/O | `src/game/engine.ts` |
| Persistence | Postgres pool + schema | `src/db/*` |
| State cache | Redis client (+ in-memory dev shim) | `src/redis/client.ts` |

The **engine is pure and side-effect free**, so it is exhaustively unit-tested and
can later run on the client for prediction without code duplication.

## Android layers (Clean Architecture / MVVM)

```
ui/         Compose screens + one GameFlowViewModel (StateFlow-driven)
  ↑ depends on
domain/     Models + MultiplayerClient interface (no Android/Socket types)
  ↑ implemented by
data/       SocketMultiplayerClient (Socket.IO), DTO mapping, token store
```

Dependencies point inward (ui → domain ← data). The UI never imports Socket.IO;
it depends only on the `MultiplayerClient` interface, bound to the concrete
implementation by Hilt. A single `ClientState` StateFlow is the source of truth,
so navigation and rendering are derived rather than imperatively juggled.

## Realtime protocol

See [`API.md`](API.md) for the full event catalogue. The core loop:

```mermaid
sequenceDiagram
    participant A as Player A
    participant S as Server (authoritative)
    participant B as Player B
    A->>S: game:move { matchId, index }
    Note over S: engine.applyMove validates<br/>(turn, occupancy, range, terminal)
    alt legal
        S-->>A: game:state (new board)
        S-->>B: game:state (new board)
        opt terminal
            S-->>A: game:over
            S-->>B: game:over
        end
    else illegal
        S-->>A: error { code }
    end
```

## Matchmaking flow

```mermaid
sequenceDiagram
    participant A as Player A
    participant B as Player B
    participant S as Server
    participant R as Redis
    A->>S: queue:join
    S->>R: enqueue A (no opponent yet)
    S-->>A: queue:searching
    B->>S: queue:join
    S->>R: pop A ↔ pair with B
    S->>S: createMatch (A=X or O random)
    S-->>A: match:found (yourSymbol, state, startAt)
    S-->>B: match:found (yourSymbol, state, startAt)
```

## Data model

```mermaid
erDiagram
    players ||--o{ sessions : has
    players ||--o{ matches : "plays (X)"
    players ||--o{ matches : "plays (O)"
    matches ||--o{ match_moves : contains
    players ||--o| leaderboard : ranks
    players ||--o{ achievements : unlocks
```

Full DDL (including roadmap tables) is in `backend/src/db/schema.sql`.

## Scaling (roadmap)

This slice runs authoritatively on a **single instance** — active game state lives
in memory on the instance that created the match. To scale horizontally:

1. Add the **Socket.IO Redis adapter** so rooms span instances.
2. Move active game state into **Redis** (keyed by matchId) so any instance can
   process a move; guard with a per-match distributed lock.
3. Make matchmaking pop-pairing atomic across instances with a **Lua script** on
   the Redis queue (the queue structure is already Redis-native).
4. Run behind Render's load balancer with sticky sessions for the WS upgrade.

The matchmaking queue and presence already live in Redis, so steps 1–3 are additive.

## Deferred features (documented, not stubbed)

| Feature | Where it hooks in |
|---------|-------------------|
| Voice chat (WebRTC/SFU) | New `voice/` service; `voice_sessions` table exists; UI slot on Match-Found screen |
| Advanced game modes | `engine.ts` already generic over board size / win-length; add mechanics per mode |
| Anti-cheat hardening | Server already authoritative; add rate limiting, move-timing analysis on `match_moves` |
| Ranked / XP economy | `leaderboard`, `statistics`, `achievements` tables exist; wire into `gameManager` on match end |
| Prediction / interpolation | Reuse pure `engine.ts` client-side for optimistic moves |
| Load / stress tests | k6/artillery against the Socket.IO endpoint |
