package com.beertracker.domain

import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

/** One catalog beer and how much of its name and brewery was read off the can, 0 to 1. */
data class CatalogMatch(val product: CatalogProduct, val score: Double)

/**
 * Ranks the catalog against free text read off a can or bottle.
 *
 * Products with the same tokenized name and brewery are one identity (the
 * same beer in another packaging), represented by the lowest article
 * number, the same rule the shelf lookup uses. Every identity token gets a
 * weight ln(1 + N / df): words shared by hundreds of beers ("bryggeri",
 * "ipa") weigh little, a word unique to one beer weighs a lot. Tokens that
 * appear only in the brewery, not in the name, count at half weight,
 * because the name is what a can prints biggest while the brewery is often
 * a logo the recognizer cannot read.
 *
 * An identity's score is the weight of its tokens that were read divided
 * by the weight of all its tokens. A token is read when an identical word
 * was read (an exact hit) or, for tokens of five letters or more, a word
 * within a small Levenshtein distance was read (a fuzzy hit). An identity
 * is a candidate only when its score reaches [MIN_SCORE] and at least one
 * distinctive token (used by at most one in fifty identities) was an exact
 * hit; without the exact requirement "bryggeri" would reach "bryggerier"
 * and "lager" would reach "fager". Candidates are ordered by score, then by
 * name in Swedish order, and capped at [MAX_MATCHES].
 *
 * Built once per catalog list; matching runs once per camera frame. It is
 * one pass over the vocabulary in which every vocabulary token of five or
 * more letters is compared against each read token whose length is within
 * tolerance, a Levenshtein edit distance computation per admitted pair, so
 * the worst case cost is roughly the vocabulary size times the read token
 * count, followed by a linear pass over the identities. That is why it
 * belongs on a background dispatcher, and why the view model only calls it
 * when a frame adds new words.
 */
class CatalogTextMatcher(products: List<CatalogProduct>) {

    private class Identity(
        val product: CatalogProduct,
        val nameTokens: Set<String>,
        val tokens: Set<String>,
    )

    private val identities: List<Identity>
    private val weights: Map<String, Double>
    private val distinctive: Set<String>
    private val maxVocabularyLength: Int

    init {
        val byKey = LinkedHashMap<Pair<String, String>, Identity>()
        for (product in products) {
            val nameTokens = tokenize(product.name)
            val breweryTokens = tokenize(product.brewery)
            val key = nameTokens.joinToString(" ") to breweryTokens.joinToString(" ")
            val existing = byKey[key]
            if (existing == null || product.articleNumber < existing.product.articleNumber) {
                byKey[key] = Identity(product, nameTokens, nameTokens + breweryTokens)
            }
        }
        identities = byKey.values.toList()

        val frequency = HashMap<String, Int>()
        for (identity in identities) {
            for (token in identity.tokens) frequency[token] = (frequency[token] ?: 0) + 1
        }
        val count = identities.size
        weights = frequency.mapValues { (_, df) -> ln(1.0 + count.toDouble() / df) }
        val distinctiveLimit = maxOf(1, count / DISTINCTIVE_DIVISOR)
        distinctive = frequency.filterValues { it <= distinctiveLimit }.keys
        maxVocabularyLength = weights.keys.maxOfOrNull { it.length } ?: 0
    }

    fun match(text: String): List<CatalogMatch> = match(tokenize(text))

    fun match(tokens: Set<String>): List<CatalogMatch> {
        if (tokens.isEmpty() || identities.isEmpty()) return emptyList()

        // Reused across every candidate pair this call considers so a frame's
        // worth of Levenshtein comparisons allocates two arrays, not two per pair.
        val previousRow = IntArray(maxVocabularyLength + 1)
        val currentRow = IntArray(maxVocabularyLength + 1)

        val exactHits = HashSet<String>()
        val seen = HashSet<String>()
        for (token in weights.keys) {
            if (token in tokens) {
                exactHits += token
                seen += token
                continue
            }
            val tolerance = fuzzyTolerance(token)
            if (tolerance > 0 &&
                tokens.any { read -> withinDistance(read, token, tolerance, previousRow, currentRow) }
            ) {
                seen += token
            }
        }

        val matches = ArrayList<CatalogMatch>()
        for (identity in identities) {
            var total = 0.0
            var hit = 0.0
            var anchored = false
            for (token in identity.tokens) {
                val factor = if (token in identity.nameTokens) 1.0 else BREWERY_ONLY_FACTOR
                val weight = weights.getValue(token) * factor
                total += weight
                if (token in seen) {
                    hit += weight
                    if (token in exactHits && token in distinctive) anchored = true
                }
            }
            if (!anchored || total == 0.0) continue
            val score = hit / total
            if (score >= MIN_SCORE) matches += CatalogMatch(identity.product, score)
        }

        if (matches.isEmpty()) return emptyList()
        val collator = Collator.getInstance(Locale("sv", "SE"))
        return matches
            .sortedWith(compareByDescending<CatalogMatch> { it.score }.thenBy(collator) { it.product.name })
            .take(MAX_MATCHES)
    }

    companion object {
        const val MAX_MATCHES = 5
        const val MIN_SCORE = 0.6
        private const val BREWERY_ONLY_FACTOR = 0.5
        private const val DISTINCTIVE_DIVISOR = 50

        // Letters that NFD does not decompose into a base letter plus a mark.
        private val SPECIAL_LETTERS = mapOf('ø' to "o", 'æ' to "ae", 'ß' to "ss", 'ł' to "l", 'đ' to "d")
        private val COMBINING_MARKS = Regex("\\p{M}+")
        private val NON_LETTERS = Regex("[^a-z]+")

        /**
         * Lower-cases, folds accented and special letters to plain a to z,
         * splits on everything else, and keeps words of two or more letters.
         * Digits vanish: on a can they are alcohol, volume and dates.
         */
        fun tokenize(text: String): Set<String> {
            val lowered = text.lowercase(Locale.ROOT)
            val replaced = buildString(lowered.length) {
                for (c in lowered) append(SPECIAL_LETTERS[c] ?: c.toString())
            }
            val folded = Normalizer.normalize(replaced, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
            return folded.split(NON_LETTERS).filterTo(LinkedHashSet()) { it.length >= 2 }
        }

        private fun fuzzyTolerance(token: String): Int = when {
            token.length >= 8 -> 2
            token.length >= 5 -> 1
            else -> 0
        }

        /**
         * Levenshtein distance of [a] and [b] is at most [max]; gives up early when it cannot
         * be. [previousRow] and [currentRow] are the caller's scratch rows, reused across many
         * calls to avoid an allocation per pair; both must have at least `b.length + 1` slots.
         * They are locals of the caller's own call, so this stays safe to call from any thread.
         */
        internal fun withinDistance(
            a: String,
            b: String,
            max: Int,
            previousRow: IntArray,
            currentRow: IntArray,
        ): Boolean {
            if (abs(a.length - b.length) > max) return false
            var previous = previousRow
            var current = currentRow
            for (j in 0..b.length) previous[j] = j
            for (i in 1..a.length) {
                current[0] = i
                var rowMin = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = min(min(previous[j] + 1, current[j - 1] + 1), previous[j - 1] + cost)
                    if (current[j] < rowMin) rowMin = current[j]
                }
                if (rowMin > max) return false
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length] <= max
        }
    }
}
