package com.beertracker.domain

import kotlin.random.Random

/**
 * The short code one phone shows and the other types to share a cellar.
 * The alphabet leaves out 0, O, 1, I and L, which look alike on a screen
 * and in handwriting. Thirty-one symbols to the power of eight is about
 * 850 billion codes.
 */
object InviteCodes {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    fun generate(random: Random = Random.Default): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /**
     * What the user typed, cleaned up: upper-cased, spaces and hyphens
     * removed. Null unless exactly LENGTH characters of ALPHABET remain.
     */
    fun normalize(input: String): String? {
        val cleaned = input.uppercase().filterNot { it.isWhitespace() || it == '-' }
        return cleaned.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }

    /** "ABCDEFGH" becomes "ABCD-EFGH"; anything not LENGTH long is returned as is. */
    fun format(code: String): String =
        if (code.length == LENGTH) code.substring(0, LENGTH / 2) + "-" + code.substring(LENGTH / 2) else code
}
