package com.beertracker.domain

/**
 * Pulls article-number candidates out of raw text recognized on a shelf
 * label. Shelf labels usually print the short number (4 or 5 digits, the
 * productNumberShort); the full article number runs up to 7 digits. Digit
 * runs embedded in longer runs, like an EAN barcode number, are not
 * candidates, which is what the lookarounds enforce.
 */
object ArticleNumberParser {

    private val candidatePattern = Regex("(?<!\\d)\\d{4,7}(?!\\d)")

    fun extractCandidates(rawText: String): List<String> =
        candidatePattern.findAll(rawText).map { it.value }.distinct().toList()
}
