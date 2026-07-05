package com.oxarena.domain.model

/**
 * The complete, immutable snapshot of the multiplayer client the UI observes.
 * A single StateFlow of this drives the whole flow (home → searching → match →
 * game → result), which keeps navigation and rendering derived from one source
 * of truth rather than scattered mutable signals.
 */
data class ClientState(
    val connection: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val playerId: String? = null,
    val phase: Phase = Phase.Idle,
    val matchInfo: MatchInfo? = null,
    val snapshot: GameSnapshot? = null,
    /** Set when the opponent left/abandoned; the local player is credited the win. */
    val opponentLeft: Boolean = false,
) {
    /** High-level phase the app is in. Navigation is derived from this. */
    enum class Phase { Idle, Searching, SearchTimedOut, InMatch }

    val isConnected: Boolean get() = connection == ConnectionStatus.CONNECTED
}
