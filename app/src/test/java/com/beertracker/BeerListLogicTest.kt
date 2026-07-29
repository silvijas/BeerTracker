package com.beertracker

import com.beertracker.domain.BeerFilter
import com.beertracker.domain.BeerSort
import com.beertracker.domain.filterAndSort
import org.junit.Assert.assertEquals
import org.junit.Test

class BeerListLogicTest {

    private val beers = listOf(
        beer(id = "a", name = "Falcon Export", brewery = "Falcon", type = "Lager",
            grade = 6, price = 15.0, dateAdded = 100L, buyAgain = true),
        beer(id = "b", name = "Punk IPA", brewery = "BrewDog", type = "IPA",
            grade = 9, price = 30.0, dateAdded = 300L, favourite = true),
        beer(id = "c", name = "Guinness Draught", brewery = "Guinness", type = "Stout",
            grade = 8, price = null, dateAdded = 200L),
        beer(id = "d", name = "Mariestads Export", brewery = "Spendrups", type = "Lager",
            grade = 8, price = 18.0, dateAdded = 400L, buyAgain = true, favourite = true),
    )

    // The four graded beers above, plus one untried beer and one tried beer with no grade.
    private val withUngraded = beers + listOf(
        beer(id = "e", name = "Shelf Find Wheat", brewery = "Nya Carnegie", type = "Wheat",
            grade = null, tried = false, price = 20.0, dateAdded = 500L, buyAgain = true),
        beer(id = "f", name = "Tasted At A Bar", brewery = "Omnipollo", type = "Sour",
            grade = null, tried = true, price = 40.0, dateAdded = 250L),
    )

    @Test
    fun `query matches name brewery and type case-insensitively`() {
        assertEquals(listOf("b"), result(BeerFilter(query = "punk")).map { it.id })
        assertEquals(listOf("c"), result(BeerFilter(query = "GUINN")).map { it.id })
        assertEquals(setOf("a", "d"), result(BeerFilter(query = "lager")).map { it.id }.toSet())
    }

    @Test
    fun `buy again filter keeps only flagged beers`() {
        assertEquals(setOf("a", "d"), result(BeerFilter(buyAgainOnly = true)).map { it.id }.toSet())
    }

    @Test
    fun `favourites filter keeps only starred beers`() {
        assertEquals(setOf("b", "d"), result(BeerFilter(favouritesOnly = true)).map { it.id }.toSet())
    }

    @Test
    fun `type filter matches any selected type`() {
        assertEquals(setOf("a", "d", "c"),
            result(BeerFilter(types = setOf("Lager", "Stout"))).map { it.id }.toSet())
    }

    @Test
    fun `filters combine`() {
        assertEquals(listOf("d"),
            result(BeerFilter(buyAgainOnly = true, favouritesOnly = true)).map { it.id })
    }

    @Test
    fun `not tried filter keeps only untried beers`() {
        assertEquals(listOf("e"), ungradedResult(BeerFilter(notTriedOnly = true)).map { it.id })
    }

    @Test
    fun `not tried filter combines with the other filters`() {
        assertEquals(listOf("e"),
            ungradedResult(BeerFilter(notTriedOnly = true, buyAgainOnly = true)).map { it.id })
        assertEquals(emptyList<String>(),
            ungradedResult(BeerFilter(notTriedOnly = true, favouritesOnly = true)).map { it.id })
        assertEquals(emptyList<String>(),
            ungradedResult(BeerFilter(notTriedOnly = true, types = setOf("Lager"))).map { it.id })
    }

    @Test
    fun `sort by grade descends with newest first on ties`() {
        assertEquals(listOf("b", "d", "c", "a"), result(sort = BeerSort.GRADE).map { it.id })
    }

    @Test
    fun `sort by grade puts ungraded beers last, newest of them first`() {
        assertEquals(listOf("b", "d", "c", "a", "e", "f"),
            ungradedResult(sort = BeerSort.GRADE).map { it.id })
    }

    @Test
    fun `sort by price ascends with null prices last`() {
        assertEquals(listOf("a", "d", "b", "c"), result(sort = BeerSort.PRICE).map { it.id })
    }

    @Test
    fun `sort by price ignores whether a beer has a grade`() {
        assertEquals(listOf("a", "d", "e", "b", "f", "c"),
            ungradedResult(sort = BeerSort.PRICE).map { it.id })
    }

    @Test
    fun `sort by name is alphabetical`() {
        assertEquals(listOf("a", "c", "d", "b"), result(sort = BeerSort.NAME_BREWERY).map { it.id })
    }

    @Test
    fun `sort by date added puts newest first`() {
        assertEquals(listOf("d", "b", "c", "a"), result(sort = BeerSort.DATE_ADDED).map { it.id })
    }

    private fun result(filter: BeerFilter = BeerFilter(), sort: BeerSort = BeerSort.GRADE) =
        filterAndSort(beers, filter, sort)

    private fun ungradedResult(filter: BeerFilter = BeerFilter(), sort: BeerSort = BeerSort.GRADE) =
        filterAndSort(withUngraded, filter, sort)
}
