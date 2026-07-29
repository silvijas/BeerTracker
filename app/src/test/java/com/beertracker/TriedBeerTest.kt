package com.beertracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
        val b = beer(grade = 7, tried = true)
        assertEquals(7, b.grade)
        assertTrue(b.tried)
    }

    @Test
    fun `a grade on a beer that is not tried is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 5, tried = false) }
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 7, tried = false) }
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 10, tried = false) }
    }

    @Test
    fun `an out of range grade is rejected even when tried`() {
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 4, tried = true) }
        assertThrows(IllegalArgumentException::class.java) { beer(grade = 11, tried = true) }
    }
}
