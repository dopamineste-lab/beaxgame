package com.oxarena.domain.repository

import com.oxarena.domain.model.ClientState
import com.oxarena.domain.model.GameError
import com.oxarena.domain.model.GameMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over the realtime multiplayer transport. The UI layer depends only
 * on this interface (Clean Architecture); the concrete Socket.IO implementation
 * lives in the data layer. Exposes one observable [state] plus a transient
 * [errors] stream and imperative commands.
 */
interface MultiplayerClient {
    /** Single source of truth for connection + matchmaking + game state. */
    val state: StateFlow<ClientState>

    /** One-off, non-sticky errors (e.g. rejected move, server error). */
    val errors: Flow<GameError>

    /** Open the socket and establish an anonymous session (idempotent). */
    fun connect()

    /** Enter matchmaking for [mode]. */
    fun joinQueue(mode: GameMode = GameMode.CLASSIC)

    /** Leave matchmaking. */
    fun cancelQueue()

    /** Attempt to place a mark at [index]; the server validates authoritatively. */
    fun makeMove(index: Int)

    /** Forfeit / leave the current match. */
    fun leaveMatch()

    /** Reset local match state back to the home phase (after a finished game). */
    fun returnToHome()

    /** Close the socket (e.g. on process teardown). */
    fun disconnect()
}
