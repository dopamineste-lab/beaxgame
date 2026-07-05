package com.oxarena

import com.oxarena.domain.model.GameMode
import com.oxarena.domain.model.GameStatus
import com.oxarena.domain.model.PlayerSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for the wire↔domain mappings. These run on the local JVM
 * (no device needed) and guard against protocol drift with the backend.
 */
class DomainMappingTest {

    @Test
    fun playerSymbol_roundTrips() {
        assertEquals(PlayerSymbol.X, PlayerSymbol.fromWire("X"))
        assertEquals(PlayerSymbol.O, PlayerSymbol.fromWire("O"))
        assertNull(PlayerSymbol.fromWire(null))
        assertNull(PlayerSymbol.fromWire("Z"))
        assertEquals("X", PlayerSymbol.X.wire)
    }

    @Test
    fun gameMode_defaultsToClassic_onUnknown() {
        assertEquals(GameMode.CLASSIC, GameMode.fromWire("classic"))
        assertEquals(GameMode.NIGHTMARE, GameMode.fromWire("nightmare"))
        assertEquals(GameMode.CLASSIC, GameMode.fromWire("does-not-exist"))
        assertEquals(GameMode.CLASSIC, GameMode.fromWire(null))
    }

    @Test
    fun gameStatus_mapsAndFlagsTerminal() {
        assertEquals(GameStatus.ACTIVE, GameStatus.fromWire("active"))
        assertEquals(GameStatus.X_WON, GameStatus.fromWire("x_won"))
        assertEquals(GameStatus.DRAW, GameStatus.fromWire("draw"))
        assertTrue(GameStatus.X_WON.isOver)
        assertTrue(GameStatus.DRAW.isOver)
        assertEquals(false, GameStatus.ACTIVE.isOver)
    }
}
