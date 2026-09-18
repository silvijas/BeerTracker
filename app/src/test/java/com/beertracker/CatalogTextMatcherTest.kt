package com.beertracker

import com.beertracker.domain.CatalogTextMatcher
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogTextMatcherTest {

    private val prodigal = catalogProduct(
        articleNumber = "1000101", articleNumberShort = "10001",
        name = "Omnipollo Prodigal Pale Ale", brewery = "Omnipollo",
    )
    private val bianca = catalogProduct(
        articleNumber = "1000201", articleNumberShort = "10002",
        name = "Omnipollo Bianca", brewery = "Omnipollo",
    )
    private val zodiak = catalogProduct(
        articleNumber = "1000301", articleNumberShort = "10003",
        name = "Omnipollo Zodiak IPA", brewery = "Omnipollo",
    )
    private val punk = catalogProduct(
        articleNumber = "1000401", articleNumberShort = "10004",
        name = "Brewdog Punk IPA", brewery = "BrewDog",
    )
    private val mariestadsCan = catalogProduct(
        articleNumber = "1000601", articleNumberShort = "10006",
        name = "Mariestads Export", brewery = "Spendrups", volumeMl = 500,
    )
    private val mariestadsBottle = catalogProduct(
        articleNumber = "1000501", articleNumberShort = "10005",
        name = "Mariestads Export", brewery = "Spendrups", volumeMl = 330,
    )
    private val skanes = catalogProduct(
        articleNumber = "1000701", articleNumberShort = "10007",
        name = "Skånes Pale Ale", brewery = "Skånebryggeriet",
    )
    private val passion = catalogProduct(
        articleNumber = "1000801", articleNumberShort = "10008",
        name = "Poppels Passion Pale Ale", brewery = "Poppels Bryggeri",
    )
    private val toOl = catalogProduct(
        articleNumber = "1000901", articleNumberShort = "10009",
        name = "To Øl Gose to Hollywood", brewery = "To Øl",
    )
    private val fager = catalogProduct(
        articleNumber = "1001001", articleNumberShort = "10010",
        name = "Fager Lager", brewery = "Fager",
    )

    /**
     * Filler beers stand in for the thousands of catalog products that
     * share the everyday words. They make "pale", "ale", "ipa", "lager"
     * and "bryggeri" common, so the distinctive rule behaves as it does on
     * the real catalog. Each filler has a unique two letter tag so it stays
     * a separate identity.
     */
    private val fillers = (0 until 104).map { i ->
        val tag = ("" + ('a' + i / 4) + ('a' + i % 4)).uppercase()
        val style = when (i % 3) {
            0 -> "Pale Ale"
            1 -> "IPA"
            else -> "Lager"
        }
        catalogProduct(
            articleNumber = (9000000 + i).toString(),
            articleNumberShort = null,
            name = "Filler $style $tag",
            brewery = "Filler Bryggeri",
        )
    }

    private val matcher = CatalogTextMatcher(
        listOf(
            prodigal, bianca, zodiak, punk, mariestadsCan, mariestadsBottle,
            skanes, passion, toOl, fager,
        ) + fillers,
    )

    private fun names(text: String) = matcher.match(text).map { it.product.name }

    @Test
    fun `the full name and brewery rank that beer first with a perfect score`() {
        val matches = matcher.match("OMNIPOLLO\nPRODIGAL PALE ALE\n5,2% VOL 330 ML")

        assertEquals("Omnipollo Prodigal Pale Ale", matches.first().product.name)
        assertEquals(1.0, matches.first().score, 0.0001)
    }

    @Test
    fun `name words alone match when one of them is distinctive`() {
        assertEquals(listOf("Omnipollo Prodigal Pale Ale"), names("PRODIGAL PALE ALE"))
    }

    @Test
    fun `a brewery with several beers does not match on its name alone`() {
        assertEquals(emptyList<String>(), names("OMNIPOLLO"))
    }

    @Test
    fun `everyday words alone match nothing`() {
        assertEquals(emptyList<String>(), names("PALE ALE IPA BRYGGERI"))
    }

    @Test
    fun `one wrong letter in a long word still matches through the fuzzy rule`() {
        assertEquals(listOf("Brewdog Punk IPA"), names("BREWDOQ PUNK IPA"))
    }

    @Test
    fun `short words only match exactly`() {
        assertEquals(emptyList<String>(), names("PUNQ IPA"))
    }

    @Test
    fun `a common word cannot reach a distinctive word through the fuzzy rule`() {
        // "lager" is within distance 1 of "fager", but the anchor must be exact.
        assertEquals(emptyList<String>(), names("LAGER"))
        assertEquals(listOf("Fager Lager"), names("FAGER LAGER"))
    }

    @Test
    fun `swedish letters fold so a plain ascii reading still matches`() {
        assertEquals(listOf("Skånes Pale Ale"), names("SKANES PALE ALE"))
    }

    @Test
    fun `the danish o folds so a plain ascii reading still matches`() {
        assertEquals(listOf("To Øl Gose to Hollywood"), names("TO OL GOSE TO HOLLYWOOD"))
    }

    @Test
    fun `digits and units never contribute`() {
        assertEquals(emptyList<String>(), names("5,2 % VOL 330 ML 7310401012345"))
    }

    @Test
    fun `two packagings of the same beer give one result with the lowest article number`() {
        val matches = matcher.match("MARIESTADS EXPORT")

        assertEquals(1, matches.size)
        assertEquals("1000501", matches.single().product.articleNumber)
    }

    @Test
    fun `results are ordered by score`() {
        assertEquals(
            listOf("Omnipollo Prodigal Pale Ale", "Omnipollo Zodiak IPA"),
            names("OMNIPOLLO PRODIGAL PALE ALE ZODIAK"),
        )
    }

    @Test
    fun `results are capped at five and equal scores follow swedish name order`() {
        val house = (0 until 7).map { i ->
            val tag = ("" + ('a' + i) + ('a' + i)).uppercase()
            catalogProduct(
                articleNumber = (2000000 + i).toString(),
                articleNumberShort = null,
                name = "Husbryggeriet $tag",
                brewery = "Husbryggeriet",
            )
        }
        val small = CatalogTextMatcher(house)

        val matched = small.match("HUSBRYGGERIET AA BB CC DD EE FF").map { it.product.name }

        assertEquals(
            listOf(
                "Husbryggeriet AA", "Husbryggeriet BB", "Husbryggeriet CC",
                "Husbryggeriet DD", "Husbryggeriet EE",
            ),
            matched,
        )
    }

    @Test
    fun `empty text and an empty catalog give an empty list`() {
        assertEquals(emptyList<String>(), names(""))
        assertEquals(0, CatalogTextMatcher(emptyList()).match("OMNIPOLLO PRODIGAL").size)
    }

    @Test
    fun `tokenize lower cases, folds letters, and drops digits and single letters`() {
        assertEquals(
            setOf("prodigal", "pale", "ale", "ml"),
            CatalogTextMatcher.tokenize("Prodigal Pale Ale 5,2% 330 ml x"),
        )
        assertEquals(
            setOf("skanebryggeriet", "to", "ol"),
            CatalogTextMatcher.tokenize("Skånebryggeriet To Øl"),
        )
        assertEquals(emptySet<String>(), CatalogTextMatcher.tokenize("7310401012345"))
    }
}
