package com.beertracker

import com.beertracker.domain.ArticleNumberParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleNumberParserTest {

    @Test
    fun `finds runs of five to seven digits`() {
        assertEquals(listOf("13245"), ArticleNumberParser.extractCandidates("Nr 13245"))
        assertEquals(listOf("132451"), ArticleNumberParser.extractCandidates("132451"))
        assertEquals(listOf("1324515"), ArticleNumberParser.extractCandidates("1324515"))
    }

    @Test
    fun `ignores shorter runs like prices volumes and percentages`() {
        assertEquals(
            emptyList<String>(),
            ArticleNumberParser.extractCandidates("5,2 % vol 330 ml 29:90"),
        )
    }

    @Test
    fun `accepts a bare four digit run`() {
        assertEquals(listOf("1017"), ArticleNumberParser.extractCandidates("1017"))
    }

    @Test
    fun `rejects a bare three digit run`() {
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates("101"))
    }

    @Test
    fun `ignores longer runs like ean barcodes`() {
        assertEquals(
            emptyList<String>(),
            ArticleNumberParser.extractCandidates("7310401012345"),
        )
    }

    @Test
    fun `a realistic shelf label yields exactly the article number`() {
        val label = "Omnipollo\nProdigal Pale Ale\n5,2 % vol 330 ml\nNr 13245\n29:90"
        assertEquals(listOf("13245"), ArticleNumberParser.extractCandidates(label))
    }

    @Test
    fun `keeps first-seen order among equal lengths and drops duplicates`() {
        assertEquals(
            listOf("13245", "10005"),
            ArticleNumberParser.extractCandidates("13245 10005 13245"),
        )
    }

    @Test
    fun `puts longer runs first so the real article number is tried before a stray short one`() {
        // A four digit run (here a year) appears before the article number in
        // the text, but the seven digit run is the far more specific match.
        assertEquals(
            listOf("1324515", "2027"),
            ArticleNumberParser.extractCandidates("Best before 2027\nNr 1324515"),
        )
    }

    @Test
    fun `a run followed by a volume unit is a volume, not an article number`() {
        // Large formats print "1500 ml", which is also a valid short number
        // for an unrelated product in the catalog.
        assertEquals(
            listOf("1017"),
            ArticleNumberParser.extractCandidates("Nr 1017\n1500 ml"),
        )
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates("1500ml"))
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates("3000 cl"))
    }

    @Test
    fun `a word that merely starts with a unit letter does not hide the number`() {
        assertEquals(listOf("1017"), ArticleNumberParser.extractCandidates("1017 Lager"))
        assertEquals(listOf("1017"), ArticleNumberParser.extractCandidates("1017 Malt"))
    }

    @Test
    fun `empty and digitless text give an empty list`() {
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates(""))
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates("IPA hoppy"))
    }
}
