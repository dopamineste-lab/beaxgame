package com.oxarena.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.oxarena.domain.model.ClientState
import com.oxarena.ui.GameFlowViewModel
import com.oxarena.ui.game.GameScreen
import com.oxarena.ui.home.HomeScreen
import com.oxarena.ui.matchfound.MatchFoundScreen
import com.oxarena.ui.result.ResultScreen
import com.oxarena.ui.searching.SearchingScreen
import com.oxarena.ui.splash.SplashScreen
import kotlinx.coroutines.flow.collectLatest
import android.widget.Toast

/** Navigation destinations. */
object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val SEARCHING = "searching"
    const val MATCH_FOUND = "match_found"
    const val GAME = "game"
    const val RESULT = "result"
}

/**
 * Central navigation. Screens issue commands through the shared [GameFlowViewModel];
 * asynchronous server-driven transitions (match found, opponent left) are handled
 * here by observing [ClientState] so no screen has to poll.
 */
@Composable
fun OxNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: GameFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Surface transient errors (rejected moves, connection issues) as toasts.
    LaunchedEffect(Unit) {
        viewModel.errors.collectLatest { err ->
            // NOT_YOUR_TURN / CELL_TAKEN are benign UI races; skip the noise.
            if (err.code !in IGNORED_ERROR_CODES) {
                Toast.makeText(context, err.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                connected = state.isConnected,
                onReady = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                playerId = state.playerId,
                onPlay = {
                    viewModel.play()
                    navController.navigateSingleTop(Routes.SEARCHING)
                },
            )
            // If a match was resumed (reconnect) while on Home, jump into it.
            LaunchedEffect(state.phase) {
                if (state.phase == ClientState.Phase.InMatch) {
                    navController.navigateSingleTop(Routes.MATCH_FOUND)
                }
            }
        }

        composable(Routes.SEARCHING) {
            SearchingScreen(
                timedOut = state.phase == ClientState.Phase.SearchTimedOut,
                onCancel = {
                    viewModel.cancelSearch()
                    navController.popBackToHome()
                },
                onRetry = { viewModel.play() },
            )
            LaunchedEffect(state.phase) {
                if (state.phase == ClientState.Phase.InMatch) {
                    navController.navigate(Routes.MATCH_FOUND) {
                        popUpTo(Routes.SEARCHING) { inclusive = true }
                    }
                }
            }
        }

        composable(Routes.MATCH_FOUND) {
            val info = state.matchInfo
            val snapshot = state.snapshot
            if (info != null && snapshot != null) {
                MatchFoundScreen(
                    matchInfo = info,
                    onCountdownComplete = {
                        navController.navigate(Routes.GAME) {
                            popUpTo(Routes.MATCH_FOUND) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(Routes.GAME) {
            GameScreen(
                state = state,
                onCellTap = viewModel::makeMove,
                onLeave = {
                    viewModel.leaveMatch()
                    navController.popBackToHome()
                },
                onContinue = { navController.navigateSingleTop(Routes.RESULT) },
            )
        }

        composable(Routes.RESULT) {
            ResultScreen(
                state = state,
                onNextMatch = {
                    viewModel.play()
                    navController.navigate(Routes.SEARCHING) {
                        popUpTo(Routes.HOME)
                    }
                },
                onHome = {
                    viewModel.backToHome()
                    navController.popBackToHome()
                },
            )
        }
    }
}

private val IGNORED_ERROR_CODES = setOf("NOT_YOUR_TURN", "CELL_TAKEN", "GAME_OVER")

private fun NavHostController.navigateSingleTop(route: String) =
    navigate(route) { launchSingleTop = true }

private fun NavHostController.popBackToHome() {
    navigate(Routes.HOME) {
        popUpTo(Routes.HOME) { inclusive = true }
        launchSingleTop = true
    }
}
