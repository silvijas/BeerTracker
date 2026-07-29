package com.beertracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TriedBeerTest {

    @Test
    fun `grade 5 and 10 are accepted`() {
        assertEquals(5, beer(grade = 5).grade)
        assertEquals(10, beer(grade = 10).grade)
    }

    @Test
    fun `grade below 5 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 4) }
    }

    @Test
    fun `grade above 10 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 11) }
    }
}
