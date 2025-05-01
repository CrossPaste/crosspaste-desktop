package com.crosspaste.paste

import com.ibm.icu.text.BreakIterator
import java.util.Locale

class DesktopSearchContentService : SearchContentService {

    override fun createSearchContent(
        source: String?,
        pasteItemSearchContent: String?,
    ): String {
        val tokens = mutableListOf<String>()

        source?.let {
            tokens.add(it)
        }

        pasteItemSearchContent?.let {
            setTokens(it, tokens)
        }

        return tokens.distinct().filterNot { it.isEmpty() }.joinToString(" ")
    }

    override fun createSearchTerms(queryString: String): List<String> {
        val tokens = mutableListOf<String>()
        setTokens(queryString, tokens)
        return tokens.distinct().filterNot { it.isEmpty() }
    }

    private fun setTokens(
        string: String,
        tokens: MutableList<String>,
    ) {
        val breaker = BreakIterator.getWordInstance(Locale.ROOT)
        breaker.setText(string)
        var start = breaker.first()
        var end = breaker.next()
        while (end != BreakIterator.DONE) {
            val word = string.substring(start, end).trim()
            if (word.isNotEmpty() && word.any { char -> char.isLetterOrDigit() }) {
                tokens.add(word)
            }
            start = end
            end = breaker.next()
        }
    }
}
