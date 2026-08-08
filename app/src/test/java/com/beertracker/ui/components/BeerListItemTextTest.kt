package com.beertracker.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BeerListItemTextTest {

    @Test
    fun `subtitle is absent when brewery and type are blank`() {
        assertEquals(null, beerListSubtitle("", "  "))
    }

    @Test
    fun `subtitle joins available brewery and type`() {
        assertEquals("Brewery, Lager", beerListSubtitle("Brewery", "Lager"))
        assertEquals("Lager", beerListSubtitle("", "Lager"))
    }
}
