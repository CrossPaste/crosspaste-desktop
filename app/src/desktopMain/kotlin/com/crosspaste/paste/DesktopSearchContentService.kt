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
            val breaker = BreakIterator.getWordInstance(Locale.ROOT)
            breaker.setText(it)
            var start = breaker.first()
            var end = breaker.next()
            while (end != BreakIterator.DONE) {
                val word = it.substring(start, end).trim()
                if (word.isNotEmpty() && word.any { char -> char.isLetterOrDigit() }) {
                    tokens.add(word)
                }
                start = end
                end = breaker.next()
            }
        }

        return tokens.joinToString(" ")
    }
}
