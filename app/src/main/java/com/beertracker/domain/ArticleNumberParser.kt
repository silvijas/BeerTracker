package com.beertracker.domain

/**
 * Pulls article-number candidates out of raw text recognized on a shelf
 * label. Shelf labels usually print the short number (4 or 5 digits, the
 * productNumberShort); the full article number runs up to 7 digits.
 *
 * Three rules decide what comes out, in this order:
 *
 * 1. A candidate is a run of 4 to 7 digits that is not part of a longer run.
 *    That keeps an EAN barcode (13 digits) from being chopped into pieces.
 * 2. A run directly followed by a volume unit ("1500 ml", "3000 cl") is a
 *    volume, not an article number, and is dropped. Without this rule a
 *    large-format bottle's "1500 ml" would look up whichever product happens
 *    to have 1500 as its short number.
 * 3. Longer runs come first in the result. A seven digit run can only be a
 *    full article number, while a four digit run might be a year or a
 *    volume, so the caller tries the most specific number first. Runs of
 *    equal length keep the order they appeared in the text.
 */
object ArticleNumberParser {

    // (?<!\d) and (?!\d): not part of a longer digit run (rule 1).
    // (?!\s?(?:ml|cl|l)\b): not followed by a volume unit (rule 2). The \b
    // stops "l" from matching the first letter of a word like "Lager".
    private val candidatePattern = Regex(
        "(?<!\\d)\\d{4,7}(?!\\d)(?!\\s?(?:ml|cl|l)\\b)",
        RegexOption.IGNORE_CASE,
    )

    fun extractCandidates(rawText: String): List<String> =
        candidatePattern.findAll(rawText)
            .map { it.value }
            .distinct()
            // Stable sort, so equal lengths stay in text order (rule 3).
            .sortedByDescending { it.length }
            .toList()
}
