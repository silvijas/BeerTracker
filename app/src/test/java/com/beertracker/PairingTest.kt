package com.beertracker

import com.beertracker.domain.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingTest {

    @Test
    fun `every entry round trips through fromSymbol and fromLabel`() {
        Pairing.entries.forEach { pairing ->
            assertEquals(pairing, Pairing.fromSymbol(pairing.symbol))
            assertEquals(pairing, Pairing.fromLabel(pairing.label))
        }
    }

    @Test
    fun `labels and symbols are unique`() {
        assertEquals(Pairing.entries.size, Pairing.entries.map { it.label }.toSet().size)
        assertEquals(Pairing.entries.size, Pairing.entries.map { it.symbol }.toSet().size)
    }

    @Test
    fun `unknown input is null rather than a wrong guess`() {
        assertNull(Pairing.fromSymbol("Choklad"))
        assertNull(Pairing.fromLabel("Tacos"))
        assertNull(Pairing.fromLabel(""))
    }

    @Test
    fun `lookups are exact, not case insensitive or trimmed`() {
        assertNull(Pairing.fromSymbol("fläsk"))
        assertNull(Pairing.fromLabel(" Pork"))
    }

    @Test
    fun `declaration order groups the meats first and the occasions last`() {
        assertEquals(
            listOf(
                "Pork", "Poultry", "Lamb", "Beef", "Game", "Fish", "Shellfish",
                "Vegetables", "Cheese", "Dessert", "Spicy food", "Asian food",
                "Buffet", "Aperitif", "Social drink",
            ),
            Pairing.entries.map { it.label },
        )
    }

    @Test
    fun `symbols match the Swedish keys the catalog API sends`() {
        assertEquals(Pairing.PORK, Pairing.fromSymbol("Fläsk"))
        assertEquals(Pairing.BEEF, Pairing.fromSymbol("Nöt"))
        assertEquals(Pairing.SOCIAL, Pairing.fromSymbol("Sällskapsdryck"))
        assertEquals(Pairing.BUFFET, Pairing.fromSymbol("Buffémat"))
    }
}
