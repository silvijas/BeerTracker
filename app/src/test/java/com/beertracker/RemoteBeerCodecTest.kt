package com.beertracker

import com.beertracker.domain.RemoteBeerCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteBeerCodecTest {

    private val full = beer(
        id = "b1", name = "Punk IPA", brewery = "BrewDog", type = "IPA",
        alcoholPercent = 5.6, volumeMl = 330, price = 29.5, grade = 4, tried = true,
        note = "hoppy", aftertaste = "bitter", goesWellWith = listOf("Beef", "Fish"),
        buyAgain = true, favourite = true, dateAdded = 12345L, imageUrl = "https://cdn/x.jpg",
    ).copy(catalogArticleNumber = "1324515", addedBy = "user-a", photoUri = "file:///photos/p.jpg")

    @Test
    fun `a full beer round-trips without its id and photo`() {
        val fields = RemoteBeerCodec.toFields(full)

        assertFalse(fields.containsKey("id"))
        assertFalse(fields.containsKey("photoUri"))
        assertEquals(full.copy(photoUri = null), RemoteBeerCodec.fromFields("b1", fields))
    }

    @Test
    fun `absent values are written as explicit nulls`() {
        val fields = RemoteBeerCodec.toFields(
            beer(id = "b2", alcoholPercent = null, volumeMl = null, price = null, grade = null, imageUrl = null),
        )

        for (key in listOf("alcoholPercent", "volumeMl", "price", "grade", "catalogArticleNumber", "addedBy", "imageUrl")) {
            assertTrue("$key must be present", fields.containsKey(key))
            assertNull(key, fields[key])
        }
    }

    @Test
    fun `whole numbers are read back from Long and from Double`() {
        val fromLong = RemoteBeerCodec.fromFields(
            "b", mapOf("name" to "A", "tried" to true, "volumeMl" to 330L, "grade" to 4L, "dateAdded" to 99L),
        )
        val fromDouble = RemoteBeerCodec.fromFields(
            "b", mapOf("name" to "A", "tried" to true, "volumeMl" to 330.0, "grade" to 4.0, "dateAdded" to 99.0),
        )

        assertEquals(330, fromLong?.volumeMl)
        assertEquals(4, fromLong?.grade)
        assertEquals(99L, fromLong?.dateAdded)
        assertEquals(fromLong, fromDouble)
    }

    @Test
    fun `decimals are read back from any number`() {
        val beer = RemoteBeerCodec.fromFields("b", mapOf("name" to "A", "alcoholPercent" to 5L, "price" to 29.5))

        assertEquals(5.0, beer?.alcoholPercent)
        assertEquals(29.5, beer?.price)
    }

    @Test
    fun `missing text reads as empty, missing flags as false, missing date as zero`() {
        val beer = RemoteBeerCodec.fromFields("b", mapOf("name" to "A"))!!

        assertEquals("", beer.brewery)
        assertEquals("", beer.type)
        assertEquals("", beer.note)
        assertEquals("", beer.aftertaste)
        assertEquals(emptyList<String>(), beer.goesWellWith)
        assertFalse(beer.tried)
        assertFalse(beer.buyAgain)
        assertFalse(beer.favourite)
        assertEquals(0L, beer.dateAdded)
        assertNull(beer.grade)
        assertNull(beer.photoUri)
    }

    @Test
    fun `a record without a usable name is rejected`() {
        assertNull(RemoteBeerCodec.fromFields("b", emptyMap()))
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to "   ")))
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to 42L)))
    }

    @Test
    fun `an illegal grade or a graded untried beer is rejected`() {
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to "A", "tried" to true, "grade" to 7L)))
        assertNull(RemoteBeerCodec.fromFields("b", mapOf("name" to "A", "tried" to false, "grade" to 3L)))
    }

    @Test
    fun `non-string pairing entries are dropped`() {
        val beer = RemoteBeerCodec.fromFields(
            "b", mapOf("name" to "A", "goesWellWith" to listOf("Beef", 3L, null, "Fish")),
        )

        assertEquals(listOf("Beef", "Fish"), beer?.goesWellWith)
    }
}
