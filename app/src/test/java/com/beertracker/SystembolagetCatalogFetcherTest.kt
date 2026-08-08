package com.beertracker

import android.app.Application
import com.beertracker.data.SystembolagetCatalogFetcher
import com.beertracker.data.mapProduct
import java.io.IOException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SystembolagetCatalogFetcherTest {

    private val sampleBeer = """
        {
         "productId": "50786609",
         "productNumber": "1324515",
         "productNumberShort": "13245",
         "productNameBold": "Omnipollo",
         "productNameThin": "Prodigal Pale Ale",
         "producerName": "Omnipollo",
         "categoryLevel1": "Öl",
         "categoryLevel2": "Ale",
         "categoryLevel3": "Pale Ale",
         "alcoholPercentage": 5.2,
         "volume": 330.0,
         "price": 25.9,
         "country": "Sverige",
         "images": [{"imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609"}]
        }
    """.trimIndent()

    private val secondBeer = """
        {
         "productNumber": "1000501",
         "productNameBold": "Second",
         "productNameThin": "Beer",
         "categoryLevel1": "Öl",
         "categoryLevel2": "Ljus lager"
        }
    """.trimIndent()

    private val sampleWine = """
        {
         "productNumber": "7000101",
         "productNameBold": "Some Wine",
         "categoryLevel1": "Vin"
        }
    """.trimIndent()

    @Test
    fun `mapProduct maps every field exactly like the seed script`() {
        assertEquals(catalogProduct(), mapProduct(JSONObject(sampleBeer)))
    }

    @Test
    fun `mapProduct fills fallbacks for missing fields`() {
        val mapped = mapProduct(JSONObject("""{"productNumber": "42", "categoryLevel1": "Öl"}"""))
        assertEquals("42", mapped.articleNumber)
        assertNull(mapped.articleNumberShort)
        assertEquals("", mapped.name)
        assertEquals("", mapped.brewery)
        assertEquals("Öl", mapped.type)
        assertNull(mapped.alcoholPercent)
        assertNull(mapped.volumeMl)
        assertNull(mapped.price)
        assertNull(mapped.country)
        assertNull(mapped.imageUrl)
    }

    @Test
    fun `mapProduct falls back to category level 3 for the type`() {
        val json = JSONObject(sampleBeer).put("categoryLevel2", JSONObject.NULL)
        assertEquals("Pale Ale", mapProduct(json).type)
    }

    @Test
    fun `walks pages until the first empty page and keeps only beer`() = runTest {
        val requested = mutableListOf<String>()
        val pages = listOf(
            """{"products": [$sampleBeer, $sampleWine]}""",
            """{"products": [$secondBeer]}""",
            """{"products": []}""",
        )
        val fetcher = SystembolagetCatalogFetcher(
            httpGet = { url ->
                requested.add(url)
                pages[requested.size - 1]
            },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val beers = fetcher.fetchAllBeers()

        assertEquals(3, requested.size)
        assertTrue(requested[0].endsWith("size=30&page=1&categoryLevel1=%C3%96l"))
        assertTrue(requested[2].endsWith("page=3&categoryLevel1=%C3%96l"))
        assertEquals(listOf("1000501", "1324515"), beers.map { it.articleNumber })
    }

    @Test
    fun `deduplicates by article number and sorts for stable results`() = runTest {
        val pages = listOf(
            """{"products": [$sampleBeer, $secondBeer]}""",
            """{"products": [$sampleBeer]}""",
            """{"products": []}""",
        )
        var call = 0
        val fetcher = SystembolagetCatalogFetcher(
            httpGet = { pages[call++] },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        assertEquals(listOf("1000501", "1324515"), fetcher.fetchAllBeers().map { it.articleNumber })
    }

    @Test
    fun `http failures propagate to the caller`() = runTest {
        val fetcher = SystembolagetCatalogFetcher(
            httpGet = { throw IOException("HTTP 503") },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        var thrown: IOException? = null
        try {
            fetcher.fetchAllBeers()
        } catch (error: IOException) {
            thrown = error
        }
        assertNotNull(thrown)
    }
}
