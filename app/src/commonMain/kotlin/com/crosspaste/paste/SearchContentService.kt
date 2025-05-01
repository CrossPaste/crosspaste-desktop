package com.crosspaste.paste

interface SearchContentService {

    fun createSearchContent(
        source: String?,
        pasteItemSearchContent: String?,
    ): String

    fun createSearchTerms(queryString: String): List<String>
}
