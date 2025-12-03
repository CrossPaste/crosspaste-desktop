package com.crosspaste.ui.model

data class SearchBaseParams(
    val favorite: Boolean,
    val pasteType: Int?,
    val sort: Boolean,
    val tag: Long?,
    val limit: Int,
)

data class SearchParams(
    val searchTerms: List<String>,
    val favorite: Boolean,
    val pasteType: Int?,
    val sort: Boolean,
    val tag: Long?,
    val limit: Int,
)
