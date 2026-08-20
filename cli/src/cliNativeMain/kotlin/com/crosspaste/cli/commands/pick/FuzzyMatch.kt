package com.crosspaste.cli.commands.pick

/**
 * Result of matching a query against one display line: a relevance [score]
 * and the character [positions] that matched (for highlighting).
 */
data class FuzzyMatch(
    val score: Int,
    val positions: List<Int>,
)

private const val SINGLE_HIT_SCORE = 1
private const val CONSECUTIVE_HIT_SCORE = 3
private const val EARLY_START_BONUS = 20

/**
 * fzf-style matching: the query splits into whitespace-separated terms, and
 * every term must match [text] as a case-insensitive subsequence. Scoring is
 * deliberately simple — consecutive hits beat scattered ones, and matches
 * starting earlier in the line beat later ones.
 *
 * Case folding is per-char ([Char.lowercaseChar]) rather than on the whole
 * string, so match positions always index into the original [text] (full
 * string lowercasing can change the length for some code points).
 *
 * An empty query matches everything with score 0. Returns null on no match.
 */
internal fun fuzzyMatch(
    query: String,
    text: String,
): FuzzyMatch? {
    val terms = query.split(' ', '\t').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return FuzzyMatch(0, emptyList())
    var score = 0
    val positions = mutableSetOf<Int>()
    for (term in terms) {
        val match = matchTerm(term, text) ?: return null
        score += match.score
        positions += match.positions
    }
    return FuzzyMatch(score, positions.sorted())
}

/** Greedy leftmost subsequence match of a single term. */
private fun matchTerm(
    term: String,
    text: String,
): FuzzyMatch? {
    val positions = ArrayList<Int>(term.length)
    var score = 0
    var previous = -2
    var searchFrom = 0
    for (ch in term) {
        val index = indexOfIgnoreCase(text, ch, searchFrom)
        if (index < 0) return null
        positions += index
        score += if (index == previous + 1) CONSECUTIVE_HIT_SCORE else SINGLE_HIT_SCORE
        previous = index
        searchFrom = index + 1
    }
    score += (EARLY_START_BONUS - positions.first()).coerceAtLeast(0)
    return FuzzyMatch(score, positions)
}

private fun indexOfIgnoreCase(
    text: String,
    ch: Char,
    from: Int,
): Int {
    val lower = ch.lowercaseChar()
    for (i in from until text.length) {
        if (text[i].lowercaseChar() == lower) return i
    }
    return -1
}
