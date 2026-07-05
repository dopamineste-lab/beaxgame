package com.oxarena.domain.model

/**
 * Domain models — the app's own vocabulary, independent of any wire format.
 * The data layer maps Socket.IO JSON payloads into these immutable types.
 */

/** A player's mark. */
enum class PlayerSymbol(val wire: String) {
    X("X"),
    O("O");

    companion object {
        fun fromWire(value: String?): PlayerSymbol? = when (value) {
            "X" -> X
            "O" -> O
            else -> null
        }
    }
}

/** Game mode. Only [CLASSIC] is playable in this slice; the rest are roadmap. */
enum class GameMode(val wire: String, val displayName: String) {
    CLASSIC("classic", "Classic"),
    HARD("hard", "Hard"),
    EXPERT("expert", "Expert"),
    INSANE("insane", "Insane"),
    NIGHTMARE("nightmare", "Nightmare"),
    IMPOSSIBLE("impossible", "Impossible");

    companion object {
        fun fromWire(value: String?): GameMode = entries.firstOrNull { it.wire == value } ?: CLASSIC
    }
}

/** Terminal / in-progress status of a game. */
enum class GameStatus(val wire: String) {
    ACTIVE("active"),
    X_WON("x_won"),
    O_WON("o_won"),
    DRAW("draw");

    val isOver: Boolean get() = this != ACTIVE

    companion object {
        fun fromWire(value: String?): GameStatus = entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

/**
 * Immutable authoritative game state as understood by the client. The server is
 * the source of truth; the UI renders this and never mutates it directly.
 */
data class GameSnapshot(
    val matchId: String,
    val mode: GameMode,
    val size: Int,
    val board: List<PlayerSymbol?>,
    val turn: PlayerSymbol,
    val status: GameStatus,
    val winner: PlayerSymbol?,
    val lastMove: Int?,
    val winningLine: List<Int>,
    val moveCount: Int,
)

/** Metadata about the current match from the player's perspective. */
data class MatchInfo(
    val matchId: String,
    val mode: GameMode,
    val yourSymbol: PlayerSymbol,
    val opponentId: String,
    /** Epoch ms when moves become legal (drives the pre-game countdown). */
    val startAt: Long,
)

/** Socket connection lifecycle. */
enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED }

/** A transient, user-facing error surfaced by the server or transport. */
data class GameError(val code: String, val message: String)
