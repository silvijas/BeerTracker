package com.beertracker

import android.app.Application
import com.beertracker.data.parseCatalogAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CatalogJsonTest {

    private val sampleAsset = """
        {
         "snapshotVersion": "2026-08-08",
         "beers": [
          {
           "articleNumber": "1324515",
           "articleNumberShort": "13245",
           "name": "Omnipollo Prodigal Pale Ale",
           "brewery": "Omnipollo",
           "type": "Ale",
           "alcoholPercent": 5.2,
           "volumeMl": 330,
           "price": 25.9,
           "country": "Sverige",
           "imageUrl": "https://product-cdn.systembolaget.se/productimages/50786609/50786609"
          },
          {
           "articleNumber": "1000501",
           "articleNumberShort": null,
           "name": "Nameless Lager",
           "brewery": "",
           "type": "Ljus lager",
           "alcoholPercent": null,
           "volumeMl": null,
           "price": null,
           "country": null,
           "imageUrl": null
          }
         ]
        }
    """.trimIndent()

    @Test
    fun `parses version and every field`() {
        val seed = parseCatalogAsset(sampleAsset)
        assertEquals("2026-08-08", seed.snapshotVersion)
        assertEquals(2, seed.beers.size)
        assertEquals(catalogProduct(), seed.beers[0])
    }

    @Test
    fun `json nulls become kotlin nulls`() {
        val second = parseCatalogAsset(sampleAsset).beers[1]
        assertEquals("1000501", second.articleNumber)
        assertNull(second.articleNumberShort)
        assertNull(second.alcoholPercent)
        assertNull(second.volumeMl)
        assertNull(second.price)
        assertNull(second.country)
        assertNull(second.imageUrl)
    }

    @Test
    fun `missing optional keys also become kotlin nulls`() {
        val seed = parseCatalogAsset(
            """{"snapshotVersion": "v", "beers": [{"articleNumber": "42", "name": "X", "brewery": "", "type": "Ale"}]}""",
        )
        val beer = seed.beers.single()
        assertNull(beer.articleNumberShort)
        assertNull(beer.alcoholPercent)
        assertNull(beer.volumeMl)
        assertNull(beer.price)
        assertNull(beer.country)
        assertNull(beer.imageUrl)
    }
}
