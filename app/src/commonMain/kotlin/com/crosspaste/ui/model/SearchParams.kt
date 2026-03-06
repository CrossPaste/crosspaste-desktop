package com.crosspaste.ui.model

data class SearchBaseParams(
    val pasteType: Int?,
    val sort: Boolean,
    val tag: Long?,
    val limit: Int,
)

data class SearchParams(
    val searchTerms: List<String>,
    val pasteType: Int?,
    val sort: Boolean,
    val tag: Long?,
    val limit: Int,
)
