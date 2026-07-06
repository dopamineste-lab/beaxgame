package com.oxarena.data.remote

import com.oxarena.core.SessionTokenStore
import com.oxarena.di.BackendUrl
import com.oxarena.domain.model.ClientState
import com.oxarena.domain.model.ConnectionStatus
import com.oxarena.domain.model.GameError
import com.oxarena.domain.model.GameMode
import com.oxarena.domain.model.GameSnapshot
import com.oxarena.domain.model.GameStatus
import com.oxarena.domain.model.MatchInfo
import com.oxarena.domain.model.PlayerSymbol
import com.oxarena.domain.repository.MultiplayerClient
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Socket.IO implementation of [MultiplayerClient].
 *
 * Owns the single realtime connection, translates inbound events into immutable
 * [ClientState] updates, and serializes outbound commands. Reconnection is handled
 * by the Socket.IO client; the anonymous session token is stored and re-supplied
 * on the handshake so the server can resume an in-progress match.
 */
@Singleton
class SocketMultiplayerClient @Inject constructor(
    @BackendUrl private val backendUrl: String,
    private val tokenStore: SessionTokenStore,
) : MultiplayerClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ClientState())
    override val state: StateFlow<ClientState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<GameError>(extraBufferCapacity = 16)
    override val errors: Flow<GameError> = _errors

    private var socket: Socket? = null
    // Mutable so reconnect handshakes pick up a freshly-minted token.
    private val authPayload = HashMap<String, String>()

    override fun connect() {
        // Reuse the existing socket if one was ever created: it already has its
        // listeners wired and Socket.IO handles reconnection itself. Building a
        // second instance here (e.g. on activity recreation while offline) would
        // duplicate every listener and double-apply state updates.
        socket?.let {
            if (!it.connected()) it.connect()
            return
        }
        _state.update { it.copy(connection = ConnectionStatus.CONNECTING) }
        scope.launch {
            tokenStore.read()?.let { authPayload["token"] = it }
            openSocket()
        }
    }

    private fun openSocket() {
        try {
            val opts = IO.Options().apply {
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionDelay = 800
                reconnectionDelayMax = 4000
                auth = authPayload
            }
            val s = IO.socket(backendUrl, opts)
            wireListeners(s)
            socket = s
            s.connect()
        } catch (t: Throwable) {
            _state.update { it.copy(connection = ConnectionStatus.DISCONNECTED) }
            emitError(GameError("SOCKET_INIT", t.message ?: "Failed to open connection"))
        }
    }

    private fun wireListeners(s: Socket) {
        s.on(Socket.EVENT_CONNECT) {
            _state.update { it.copy(connection = ConnectionStatus.CONNECTED) }
        }
        s.on(Socket.EVENT_DISCONNECT) {
            _state.update { it.copy(connection = ConnectionStatus.CONNECTING) }
        }
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            emitError(GameError("CONNECT_ERROR", (args.getOrNull(0)?.toString() ?: "connection error")))
        }

        s.on("session") { args ->
            val o = args.firstObj() ?: return@on
            val playerId = o.optString("playerId", null)
            val token = o.optString("token", null)
            if (token != null) {
                authPayload["token"] = token
                scope.launch { tokenStore.save(token) }
            }
            _state.update { it.copy(playerId = playerId) }
        }

        s.on("queue:searching") {
            _state.update { it.copy(phase = ClientState.Phase.Searching) }
        }
        s.on("queue:cancelled") {
            _state.update {
                if (it.phase == ClientState.Phase.Searching) {
                    it.copy(phase = ClientState.Phase.SearchTimedOut)
                } else it
            }
        }

        s.on("match:found") { args ->
            val o = args.firstObj() ?: return@on
            val info = parseMatchInfo(o)
            val snapshot = parseSnapshot(o.getJSONObject("state"))
            _state.update {
                it.copy(
                    phase = ClientState.Phase.InMatch,
                    matchInfo = info,
                    snapshot = snapshot,
                    opponentLeft = false,
                )
            }
        }

        s.on("game:state") { args ->
            val o = args.firstObj() ?: return@on
            val snapshot = parseSnapshot(o)
            _state.update { it.copy(snapshot = snapshot) }
        }

        s.on("game:over") { args ->
            val o = args.firstObj() ?: return@on
            // The authoritative final state already arrived via game:state; ensure
            // status reflects terminal even if events race.
            val status = GameStatus.fromWire(o.optString("status"))
            _state.update { st ->
                val snap = st.snapshot?.copy(
                    status = status,
                    winner = PlayerSymbol.fromWire(o.optString("winner", null)),
                )
                st.copy(snapshot = snap)
            }
        }

        s.on("opponent:left") {
            _state.update { it.copy(opponentLeft = true) }
        }
        s.on("opponent:reconnected") {
            _state.update { it.copy(opponentLeft = false) }
        }

        s.on("error") { args ->
            val o = args.firstObj()
            emitError(
                GameError(
                    code = o?.optString("code") ?: "ERROR",
                    message = o?.optString("message") ?: "Something went wrong",
                ),
            )
        }
    }

    override fun joinQueue(mode: GameMode) {
        _state.update {
            it.copy(
                phase = ClientState.Phase.Searching,
                matchInfo = null,
                snapshot = null,
                opponentLeft = false,
            )
        }
        socket?.emit("queue:join", JSONObject().put("mode", mode.wire))
    }

    override fun cancelQueue() {
        socket?.emit("queue:cancel")
        _state.update { it.copy(phase = ClientState.Phase.Idle) }
    }

    override fun makeMove(index: Int) {
        val matchId = _state.value.matchInfo?.matchId ?: return
        socket?.emit("game:move", JSONObject().put("matchId", matchId).put("index", index))
    }

    override fun leaveMatch() {
        val matchId = _state.value.matchInfo?.matchId
        if (matchId != null) socket?.emit("game:leave", JSONObject().put("matchId", matchId))
        returnToHome()
    }

    override fun returnToHome() {
        _state.update {
            it.copy(
                phase = ClientState.Phase.Idle,
                matchInfo = null,
                snapshot = null,
                opponentLeft = false,
            )
        }
    }

    override fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        _state.update { it.copy(connection = ConnectionStatus.DISCONNECTED) }
    }

    // ─── parsing helpers ─────────────────────────────────────────────────────

    private fun parseMatchInfo(o: JSONObject): MatchInfo = MatchInfo(
        matchId = o.getString("matchId"),
        mode = GameMode.fromWire(o.optString("mode")),
        yourSymbol = PlayerSymbol.fromWire(o.optString("yourSymbol")) ?: PlayerSymbol.X,
        opponentId = o.optString("opponentId", ""),
        startAt = o.optLong("startAt", System.currentTimeMillis()),
    )

    private fun parseSnapshot(o: JSONObject): GameSnapshot {
        val boardJson = o.getJSONArray("board")
        val board = ArrayList<PlayerSymbol?>(boardJson.length())
        for (i in 0 until boardJson.length()) {
            board.add(if (boardJson.isNull(i)) null else PlayerSymbol.fromWire(boardJson.getString(i)))
        }
        return GameSnapshot(
            matchId = o.getString("matchId"),
            mode = GameMode.fromWire(o.optString("mode")),
            size = o.optInt("size", 3),
            board = board,
            turn = PlayerSymbol.fromWire(o.optString("turn")) ?: PlayerSymbol.X,
            status = GameStatus.fromWire(o.optString("status")),
            winner = PlayerSymbol.fromWire(o.optString("winner", null)),
            lastMove = if (o.isNull("lastMove")) null else o.optInt("lastMove"),
            winningLine = parseIntArray(o.opt("winningLine")),
            moveCount = o.optInt("moveCount", 0),
        )
    }

    private fun parseIntArray(value: Any?): List<Int> {
        if (value !is JSONArray) return emptyList()
        return (0 until value.length()).map { value.getInt(it) }
    }

    private fun emitError(error: GameError) {
        scope.launch { _errors.emit(error) }
    }
}

/** First emitted Socket.IO arg as a JSONObject, or null. */
private fun Array<out Any?>?.firstObj(): JSONObject? = this?.getOrNull(0) as? JSONObject
