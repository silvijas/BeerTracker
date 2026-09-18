package com.beertracker.domain

/**
 * Finds the line of text read off a can that looks most like the beer's
 * name, for the case where nothing in the catalog matched and the user
 * finishes the entry by hand.
 *
 * A line qualifies when it has at least three letters and letters make up
 * more than half of its non-space characters; that drops "5,2 % VOL",
 * "330 ML" and barcode digits. Of the qualifying lines the one with the
 * most letters wins, and the earlier line wins a tie. The text is returned
 * as read, including its case: the user edits the field anyway, and title
 * casing would turn "IPA" into "Ipa".
 */
object CanTextParser {

    private const val MIN_LETTERS = 3

    fun guessName(text: String): String? =
        text.lineSequence()
            .map(String::trim)
            .filter(::looksLikeName)
            .maxByOrNull { line -> line.count(Char::isLetter) }

    private fun looksLikeName(line: String): Boolean {
        val letters = line.count(Char::isLetter)
        val nonSpace = line.count { !it.isWhitespace() }
        return letters >= MIN_LETTERS && letters * 2 > nonSpace
    }
}
