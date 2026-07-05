# Security Checklist

Legend: ✅ implemented · 🔜 roadmap

## Authentication & sessions
- ✅ Anonymous JWT identity — no PII collected (no email/phone/username)
- ✅ Signed tokens (HS256); server refuses to start in prod with the default secret
- ✅ Token verified on every socket handshake
- ✅ One live connection per identity (older connection evicted → `REPLACED`)
- 🔜 Token rotation cadence + revocation list (sessions table already supports it)
- 🔜 Move `JWT_SECRET` to a managed secret store; periodic rotation

## Transport
- ✅ WSS/HTTPS in production (Render TLS)
- ✅ Secure headers via `@fastify/helmet`
- ✅ CORS configurable (`CORS_ORIGIN`)
- ✅ Android cleartext restricted to local dev hosts only (network-security-config)

## Server authority & anti-cheat
- ✅ Fully authoritative: every move re-validated by the pure engine
  (turn, occupancy, range, terminal) — a modified client cannot forge state
- ✅ Match membership enforced (`NOT_IN_MATCH`) — cannot move in others' games
- ✅ Payload validation on inbound events — zod schemas on every socket event (`BAD_PAYLOAD`)
- ✅ Flood protection: per-socket token bucket (20 burst / 10 per second, `RATE_LIMITED`),
  persistent flooders force-disconnected; per-IP limit on `/api/session`
- 🔜 Move-timing analysis on `match_moves` (anti speed-hack)
- 🔜 Reports/bans pipeline (tables exist) + automated abuse detection

## Data & injection
- ✅ Parameterized SQL everywhere (no string interpolation) — SQLi-safe
- ✅ No user-generated HTML rendered → XSS surface minimal
- ✅ Least-privilege DB usage via connection string
- ✅ Schema validation (zod) with strict types/bounds on all socket payloads
- 🔜 Audit logging of sensitive actions

## Operations
- ✅ Secrets via environment variables (never committed; `.env` gitignored)
- ✅ Non-root container user
- 🔜 Dependency scanning (`npm audit` / Dependabot) in CI gate
- 🔜 Penetration test before public launch
