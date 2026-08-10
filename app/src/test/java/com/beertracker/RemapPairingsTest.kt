package com.beertracker

import com.beertracker.data.remapPairings
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SEP = "\u001F"

class RemapPairingsTest {

    @Test
    fun `red meat becomes beef`() {
        assertEquals("Beef", remapPairings("Red meat"))
    }

    @Test
    fun `salmon and white fish collapse to a single fish`() {
        assertEquals("Fish", remapPairings("Salmon${SEP}White fish"))
    }

    @Test
    fun `both pasta values are dropped`() {
        assertEquals("", remapPairings("Pasta white sauce${SEP}Pasta tomato sauce"))
    }

    @Test
    fun `dessert and unknown values are left exactly as they are`() {
        assertEquals("Dessert${SEP}Tacos", remapPairings("Dessert${SEP}Tacos"))
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals("", remapPairings(""))
    }

    @Test
    fun `order follows the stored order, first occurrence wins`() {
        assertEquals("Fish${SEP}Beef", remapPairings("White fish${SEP}Red meat${SEP}Salmon"))
    }

    @Test
    fun `a value already in the new vocabulary is untouched`() {
        assertEquals("Pork${SEP}Social drink", remapPairings("Pork${SEP}Social drink"))
    }
}
