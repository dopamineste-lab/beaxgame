# Production Readiness Checklist

Legend: ✅ done in this slice · 🔜 roadmap before large-scale launch

## Backend
- ✅ Authoritative server; all moves validated server-side
- ✅ Health (`/healthz`) + readiness (`/readyz`) endpoints
- ✅ Graceful shutdown on SIGTERM/SIGINT (drains WS, closes Redis/PG)
- ✅ Structured logging (JSON in production)
- ✅ Typed, validated env config; refuses default JWT secret in prod
- ✅ Dockerized (multi-stage, non-root runtime user)
- ✅ Reconnect grace window + duplicate-connection eviction
- ✅ Rate limiting: token bucket per socket (abusive clients disconnected) + per-IP limit on session minting
- ✅ Zod schema validation on every inbound socket payload
- 🔜 Socket.IO Redis adapter + Redis-backed game state for horizontal scale
- 🔜 Metrics (Prometheus) + tracing; alerting on error rate / queue depth
- 🔜 Load/stress testing (k6/artillery) to size instances

## Data
- ✅ PostgreSQL schema (matches, moves, sessions, players + roadmap tables)
- ✅ Idempotent schema migration file, **auto-applied on boot** (zero manual steps on Render)
- 🔜 Managed migrations tool (e.g. node-pg-migrate) once schema evolves beyond additive
- 🔜 Backups / PITR configured on the managed DB
- 🔜 Redis `noeviction` verified (set in `render.yaml`) and memory alerts

## Android
- ✅ Builds a signed-able debug APK; Clean Architecture; Hilt DI
- ✅ Immutable StateFlow UI; edge-to-edge; dark premium theme
- ✅ Reconnect/resume via persisted anonymous token
- ✅ Release signing (keystore.properties, keystore gitignored); verified 2.3MB minified signed APK
- 🔜 Play App Signing enrollment when publishing to Play Store
- 🔜 Baseline Profiles + startup optimization; R8 rules validated
- 🔜 Crash/ANR reporting (Crashlytics or Sentry)
- 🔜 Compose UI + instrumented tests in CI (emulator matrix)

## Delivery
- ✅ CI: backend typecheck/tests/smoke/build + Android build/tests
- ✅ Render Blueprint (`render.yaml`) for one-click infra
- 🔜 Staging environment + promotion flow
- 🔜 Versioned API contract tests shared by client & server
