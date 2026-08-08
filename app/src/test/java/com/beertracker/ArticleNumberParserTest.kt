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
    fun `keeps first-seen order and drops duplicates`() {
        assertEquals(
            listOf("13245", "10005"),
            ArticleNumberParser.extractCandidates("13245 10005 13245"),
        )
    }

    @Test
    fun `empty and digitless text give an empty list`() {
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates(""))
        assertEquals(emptyList<String>(), ArticleNumberParser.extractCandidates("IPA hoppy"))
    }
}
