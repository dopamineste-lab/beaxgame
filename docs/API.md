# OX Arena — API & Realtime Protocol

Base URL (local): `http://localhost:8080` · (Render): `https://<your-service>.onrender.com`

## REST

### `GET /healthz` — liveness
`200 { "status": "ok", "activeMatches": 3 }`

### `GET /readyz` — readiness (dependencies)
`200` when Redis + Postgres reachable, else `503`.
```json
{ "status": "ready", "checks": { "redis": "up", "database": "up" } }
```
(`"shim"` / `"disabled"` appear in dev mode when infra is not configured.)

### `POST /api/session` — mint anonymous session
```json
{ "token": "<jwt>", "playerId": "<uuid>", "expiresAt": 1730000000000 }
```
Optional — the Socket.IO handshake mints one automatically if none is supplied.

## WebSocket (Socket.IO)

Connect to the base URL. Authenticate by passing the token in the handshake:
```js
io(BASE_URL, { auth: { token } })   // omit token on first ever connect
```
If the token is missing/invalid the server mints a fresh anonymous identity and
returns it in the `session` event (persist it to keep identity across restarts).

### Client → Server

| Event | Payload | Meaning |
|-------|---------|---------|
| `queue:join` | `{ mode?: "classic" }` | Enter matchmaking |
| `queue:cancel` | — | Leave matchmaking |
| `game:move` | `{ matchId, index }` | Attempt a move (server validates) |
| `game:leave` | `{ matchId }` | Forfeit / leave the match |

### Server → Client

| Event | Payload | Meaning |
|-------|---------|---------|
| `session` | `{ playerId, token }` | Identity (persist `token`) |
| `queue:searching` | `{ since, timeoutMs }` | Queued, waiting |
| `queue:cancelled` | — | Removed from queue / timed out |
| `match:found` | `MatchFound` | Paired — includes your symbol + initial state |
| `game:state` | `GameState` | Authoritative board update |
| `game:over` | `{ matchId, status, winner }` | Terminal result |
| `opponent:left` | `{ matchId }` | Opponent disconnected/forfeited |
| `opponent:reconnected` | `{ matchId }` | Opponent came back |
| `error` | `{ code, message }` | Rejected action / server error |

### Payload shapes

```ts
MatchFound {
  matchId: string
  mode: "classic"
  yourSymbol: "X" | "O"
  opponentId: string
  startAt: number            // epoch ms; cosmetic countdown target
  state: GameState
}

GameState {
  matchId: string
  mode: "classic"
  size: number               // 3 for classic
  board: ("X" | "O" | null)[]  // length size*size, row-major
  turn: "X" | "O"
  status: "active" | "x_won" | "o_won" | "draw"
  winner: "X" | "O" | null
  lastMove: number | null
  winningLine: number[] | null
  moveCount: number
}
```

### Error codes

| Code | Cause |
|------|-------|
| `NOT_YOUR_TURN` | Move sent when it isn't your turn |
| `CELL_TAKEN` | Target cell already occupied |
| `OUT_OF_RANGE` | Move index outside the board |
| `GAME_OVER` | Move after the game ended |
| `MATCH_NOT_FOUND` | Unknown/removed match |
| `NOT_IN_MATCH` | Player not part of that match |
| `BAD_PAYLOAD` | Malformed move payload |
| `REPLACED` | Superseded by a newer connection for the same identity |

### Reconnect

Reconnecting with the same token within `RECONNECT_GRACE_MS` re-attaches the
player to their match; the server re-emits `match:found` (with current state) and
notifies the opponent via `opponent:reconnected`. After the grace window the match
is abandoned and the opponent is credited via `opponent:left`.
