package com.beertracker

import com.beertracker.domain.CatalogBrowseLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogBrowseLogicTest {

    private val products = listOf(
        catalogProduct(articleNumber = "100", articleNumberShort = "10", name = "Zeta Lager", brewery = "Zeta Bryggeri", type = "Lager"),
        catalogProduct(articleNumber = "200", articleNumberShort = "20", name = "Ale Star", brewery = "Star", type = "Ale"),
        catalogProduct(articleNumber = "300", articleNumberShort = "30", name = "Ölvisholt Lava", brewery = "Ölvisholt", type = "Stout"),
        catalogProduct(articleNumber = "400", articleNumberShort = "40", name = "Éphémère Apple", brewery = "Unibroue", type = "Wheat beer"),
    )

    @Test
    fun `empty and blank queries return everything in Swedish name order`() {
        val expected = listOf("Ale Star", "Éphémère Apple", "Zeta Lager", "Ölvisholt Lava")
        assertEquals(expected, CatalogBrowseLogic.filterAndSort(products, "").map { it.name })
        assertEquals(expected, CatalogBrowseLogic.filterAndSort(products, "   ").map { it.name })
    }

    @Test
    fun `matches name brewery and type ignoring case including Swedish letters`() {
        assertEquals(
            listOf("Ölvisholt Lava"),
            CatalogBrowseLogic.filterAndSort(products, "ölvis").map { it.name },
        )
        assertEquals(
            listOf("Zeta Lager"),
            CatalogBrowseLogic.filterAndSort(products, "ZETA BRY").map { it.name },
        )
        assertEquals(
            listOf("Ölvisholt Lava"),
            CatalogBrowseLogic.filterAndSort(products, "stout").map { it.name },
        )
    }

    @Test
    fun `digit queries match article numbers by prefix and still match names`() {
        assertEquals(
            listOf("Zeta Lager"),
            CatalogBrowseLogic.filterAndSort(products, "10").map { it.name },
        )
        val withNumericName = products + catalogProduct(
            articleNumber = "900",
            articleNumberShort = "90",
            name = "1664 Blanc",
            brewery = "Kronenbourg",
            type = "Wheat beer",
        )
        assertEquals(
            listOf("1664 Blanc"),
            CatalogBrowseLogic.filterAndSort(withNumericName, "1664").map { it.name },
        )
    }

    @Test
    fun `non digit queries never match article numbers`() {
        assertEquals(
            emptyList<String>(),
            CatalogBrowseLogic.filterAndSort(products, "10x").map { it.name },
        )
    }

    @Test
    fun `surrounding whitespace in the query is ignored`() {
        assertEquals(
            listOf("Ale Star"),
            CatalogBrowseLogic.filterAndSort(products, "  ale s ").map { it.name },
        )
    }
}
