package com.beertracker

import com.beertracker.domain.InviteCodes
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteCodesTest {

    @Test
    fun `generated codes have eight characters from the alphabet`() {
        repeat(50) {
            val code = InviteCodes.generate()
            assertEquals(InviteCodes.LENGTH, code.length)
            assertTrue(code, code.all { it in InviteCodes.ALPHABET })
        }
    }

    @Test
    fun `the alphabet has no look-alike characters`() {
        for (c in "0O1IL") {
            assertTrue("$c must not be in the alphabet", c !in InviteCodes.ALPHABET)
        }
    }

    @Test
    fun `generation uses the given random source`() {
        assertEquals(InviteCodes.generate(Random(7)), InviteCodes.generate(Random(7)))
    }

    @Test
    fun `normalize upper-cases and drops spaces and hyphens`() {
        assertEquals("ABCDEFGH", InviteCodes.normalize(" abcd-efgh "))
        assertEquals("ABCD2345", InviteCodes.normalize("abcd 2345"))
    }

    @Test
    fun `normalize rejects the wrong length`() {
        assertNull(InviteCodes.normalize("ABCDEFG"))
        assertNull(InviteCodes.normalize("ABCDEFGHJ"))
        assertNull(InviteCodes.normalize(""))
    }

    @Test
    fun `normalize rejects characters outside the alphabet`() {
        assertNull(InviteCodes.normalize("ABCDEFG0"))
        assertNull(InviteCodes.normalize("ABCDEFGI"))
        assertNull(InviteCodes.normalize("ABCD.FGH"))
    }

    @Test
    fun `format groups the code in two halves`() {
        assertEquals("ABCD-EFGH", InviteCodes.format("ABCDEFGH"))
    }

    @Test
    fun `format leaves an odd value alone`() {
        assertEquals("ABC", InviteCodes.format("ABC"))
    }
}
