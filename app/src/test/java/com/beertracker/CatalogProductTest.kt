package com.beertracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogProductTest {

    @Test
    fun `display image url appends the 400 pixel jpg suffix`() {
        assertEquals(
            "https://product-cdn.systembolaget.se/productimages/50786609/50786609_400.jpg",
            catalogProduct().displayImageUrl,
        )
    }

    @Test
    fun `display image url is null when there is no image`() {
        assertNull(catalogProduct(imageUrl = null).displayImageUrl)
    }
}
