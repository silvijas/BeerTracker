package com.beertracker

import com.beertracker.domain.CanTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanTextParserTest {

    @Test
    fun `picks the line with the most letters`() {
        val text = "OMNIPOLLO\nPRODIGAL PALE ALE\n5,2 % VOL\n330 ML"
        assertEquals("PRODIGAL PALE ALE", CanTextParser.guessName(text))
    }

    @Test
    fun `skips lines that are mostly digits and units`() {
        assertEquals("BREWDOG PUNK IPA", CanTextParser.guessName("BREWDOG PUNK IPA\nALC 5,2% VOL"))
        assertNull(CanTextParser.guessName("5,2 % VOL\n330 ML\n7310401012345"))
    }

    @Test
    fun `returns null for empty text`() {
        assertNull(CanTextParser.guessName(""))
        assertNull(CanTextParser.guessName("\n\n"))
    }

    @Test
    fun `the earlier of two equal lines wins`() {
        assertEquals("ABC", CanTextParser.guessName("ABC\nXYZ"))
    }

    @Test
    fun `the chosen line is trimmed`() {
        assertEquals("Punk IPA", CanTextParser.guessName("  Punk IPA  \n"))
    }
}
