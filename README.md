# OX Arena

A production-grade **real-time multiplayer Tic-Tac-Toe** platform — anonymous instant play,
authoritative backend, Android (Jetpack Compose) client. This repository is the **working
vertical slice**: a genuine end-to-end thread through the entire stack (matchmaking →
authoritative gameplay → persistence) that actually compiles and runs, built as the
foundation the larger feature set grows onto.

> **Status:** Slice 1 — Classic mode, online 1v1, real matchmaking. See
> [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the roadmap (voice chat, advanced game
> modes, anti-cheat, scaling) and where each feature hooks in.

## What works right now

- **No login.** Launch → anonymous JWT session issued automatically → tap Play.
- **Real matchmaking.** Redis-backed worldwide queue with atomic pairing, cancel, and
  duplicate-connection prevention.
- **Authoritative gameplay.** The server owns game state; every move is validated server-side.
  Clients render state broadcast over Socket.IO.
- **Persistence.** Matches and moves recorded in PostgreSQL.
- **Reconnect.** Short reconnect window so a dropped player rejoins their match.
- **Deployable.** Multi-stage Dockerfile + `render.yaml` (web + Redis + Postgres) for one-click
  Render deploy.

## Repository layout

```
ox-arena/
├── backend/          Node + TypeScript + Fastify + Socket.IO + Redis + Postgres
├── android/          Kotlin + Jetpack Compose + Hilt (Clean Architecture / MVVM)
├── docs/             Architecture, API protocol, Render deployment guide
└── .github/workflows CI (lint, typecheck, tests, build)
```

## Quick start — backend

```bash
cd backend
cp .env.example .env          # defaults work for local dev (in-memory shim, no Docker needed)
npm install
npm test                      # engine unit tests
npm run dev                   # starts server on http://localhost:8080
npm run smoke                 # spins up two clients that get matched and play a full game
```

Without `REDIS_URL` / `DATABASE_URL` set, the backend runs in **dev mode** with an in-memory
Redis shim and persistence disabled, so you can play a full match with zero infrastructure.
Point it at real Redis/Postgres (or Render) by setting those env vars.

## Quick start — Android

Open `android/` in Android Studio (Giraffe+), set `BACKEND_URL` in `local.properties`
(defaults to `http://10.0.2.2:8080` for the emulator), and run the `app` configuration on two
emulators/devices to play against yourself.

See [`docs/DEPLOYMENT_RENDER.md`](docs/DEPLOYMENT_RENDER.md) to deploy the backend to Render.

## License

MIT — see repository owner.
