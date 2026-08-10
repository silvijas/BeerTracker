package com.beertracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TriedBeerTest {

    @Test
    fun `grade 1 and 5 are accepted`() {
        assertEquals(1, beer(grade = 1).grade)
        assertEquals(5, beer(grade = 5).grade)
    }

    @Test
    fun `grade below 1 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 0) }
    }

    @Test
    fun `grade above 5 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 6) }
    }

    @Test
    fun `a beer with no grade and not tried is accepted`() {
        val b = beer(grade = null, tried = false)
        assertNull(b.grade)
        assertFalse(b.tried)
    }

    @Test
    fun `a beer with no grade that was tried is accepted`() {
        val b = beer(grade = null, tried = true)
        assertNull(b.grade)
        assertTrue(b.tried)
    }

    @Test
    fun `a graded beer is tried`() {
        val b = beer(grade = 4, tried = true)
        assertEquals(4, b.grade)
        assertTrue(b.tried)
    }

    @Test
    fun `a grade on a beer that is not tried is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 1, tried = false) }
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 3, tried = false) }
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 5, tried = false) }
    }

    @Test
    fun `an out of range grade is rejected even when tried`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 0, tried = true) }
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 6, tried = true) }
    }

    @Test
    fun `displayImageUrl prefers the user's photo over the catalog image`() {
        val b = beer(imageUrl = "https://cdn/x.jpg").copy(photoUri = "file:///p.jpg")
        assertEquals("file:///p.jpg", b.displayImageUrl)
    }

    @Test
    fun `displayImageUrl falls back to the catalog image`() {
        val b = beer(imageUrl = "https://cdn/x.jpg").copy(photoUri = null)
        assertEquals("https://cdn/x.jpg", b.displayImageUrl)
    }

    @Test
    fun `displayImageUrl is null when the beer has neither`() {
        assertNull(beer(imageUrl = null).copy(photoUri = null).displayImageUrl)
    }
}
