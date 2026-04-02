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
