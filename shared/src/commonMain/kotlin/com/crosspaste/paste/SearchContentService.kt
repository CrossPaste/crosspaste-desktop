package com.crosspaste.paste

interface SearchContentService {

    fun createSearchContent(
        source: String?,
        content: String?,
    ): String =
        createSearchContent(
            source,
            listOfNotNull(content),
        )

    fun createSearchContent(
        source: String?,
        searchContentList: List<String>,
    ): String

    fun createSearchTerms(queryString: String): List<String>
}
