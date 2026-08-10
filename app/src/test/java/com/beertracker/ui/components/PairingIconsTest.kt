package com.beertracker.ui.components

import com.beertracker.domain.Pairing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingIconsTest {

    @Test
    fun `every pairing has its own icon`() {
        val icons = Pairing.entries.map { pairingIcon(it) }
        assertEquals(Pairing.entries.size, icons.map { it.name }.toSet().size)
    }

    @Test
    fun `every pairing has its own label resource`() {
        val ids = Pairing.entries.map { pairingLabelRes(it) }
        assertEquals(Pairing.entries.size, ids.toSet().size)
        assertEquals(0, ids.count { it == 0 })
    }

    @Test
    fun `icons are drawn on the same 24 by 24 grid`() {
        Pairing.entries.forEach { pairing ->
            val icon = pairingIcon(pairing)
            assertEquals(24f, icon.viewportWidth, 0f)
            assertEquals(24f, icon.viewportHeight, 0f)
        }
    }

    @Test
    fun `icon names are prefixed so they cannot collide with other vectors`() {
        Pairing.entries.forEach { pairing ->
            assertNotEquals("", pairingIcon(pairing).name)
            assertTrue(pairingIcon(pairing).name.startsWith("Pairing"))
        }
    }
}
