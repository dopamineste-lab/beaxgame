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
- 🔜 Rate limiting per connection/IP (Redis token bucket)
- 🔜 Socket.IO Redis adapter + Redis-backed game state for horizontal scale
- 🔜 Metrics (Prometheus) + tracing; alerting on error rate / queue depth
- 🔜 Load/stress testing (k6/artillery) to size instances

## Data
- ✅ PostgreSQL schema (matches, moves, sessions, players + roadmap tables)
- ✅ Idempotent schema migration file
- 🔜 Managed migrations tool (e.g. node-pg-migrate) instead of raw SQL apply
- 🔜 Backups / PITR configured on the managed DB
- 🔜 Redis `noeviction` verified (set in `render.yaml`) and memory alerts

## Android
- ✅ Builds a signed-able debug APK; Clean Architecture; Hilt DI
- ✅ Immutable StateFlow UI; edge-to-edge; dark premium theme
- ✅ Reconnect/resume via persisted anonymous token
- 🔜 Release signing config + Play App Signing
- 🔜 Baseline Profiles + startup optimization; R8 rules validated
- 🔜 Crash/ANR reporting (Crashlytics or Sentry)
- 🔜 Compose UI + instrumented tests in CI (emulator matrix)

## Delivery
- ✅ CI: backend typecheck/tests/smoke/build + Android build/tests
- ✅ Render Blueprint (`render.yaml`) for one-click infra
- 🔜 Staging environment + promotion flow
- 🔜 Versioned API contract tests shared by client & server
