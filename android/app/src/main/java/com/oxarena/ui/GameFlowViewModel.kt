package com.oxarena.ui

import androidx.lifecycle.ViewModel
import com.oxarena.domain.model.ClientState
import com.oxarena.domain.model.GameError
import com.oxarena.domain.model.GameMode
import com.oxarena.domain.repository.MultiplayerClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The single ViewModel shared across the whole match flow. Because the underlying
 * [MultiplayerClient] is a singleton, one ViewModel scoped to the nav graph gives
 * every screen the same immutable [ClientState] to render, and one place to issue
 * commands — keeping navigation and UI derived from a single source of truth.
 */
@HiltViewModel
class GameFlowViewModel @Inject constructor(
    private val client: MultiplayerClient,
) : ViewModel() {

    val state: StateFlow<ClientState> = client.state
    val errors: Flow<GameError> = client.errors

    init {
        // Establish the anonymous session as soon as the app graph is created.
        client.connect()
    }

    fun play() = client.joinQueue(GameMode.CLASSIC)
    fun cancelSearch() = client.cancelQueue()
    fun makeMove(index: Int) = client.makeMove(index)
    fun leaveMatch() = client.leaveMatch()
    fun backToHome() = client.returnToHome()
}
