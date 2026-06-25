package com.crosspaste.ui.model

data class SearchBaseParams(
    val pasteTypeList: List<Int>,
    val sort: Boolean,
    val tag: Long?,
    val limit: Int,
)

data class SearchParams(
    val searchTerms: List<String>,
    val pasteTypeList: List<Int>,
    val sort: Boolean,
    val tag: Long?,
    val limit: Int,
)

/**
 * The identity of a search query, independent of the pagination cursor ([SearchParams.limit]).
 *
 * Selection and scroll position should reset only when the query identity changes (a new term,
 * sort, type filter, or tag) — never when the list merely grows by loading another page. Keeping
 * the limit out of this type makes that distinction structural rather than something each call
 * site has to remember to encode.
 */
data class SearchQuery(
    val searchTerms: List<String>,
    val pasteTypeList: List<Int>,
    val sort: Boolean,
    val tag: Long?,
)
