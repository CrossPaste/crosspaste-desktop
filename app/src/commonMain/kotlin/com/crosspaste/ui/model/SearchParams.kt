package com.crosspaste.ui.model

data class SearchBaseParams(
    val favorite: Boolean,
    val sort: Boolean,
    val pasteType: Int?,
    val limit: Int,
) {

    override fun toString(): String =
        "SearchBaseParams(favorite=$favorite, sort=$sort, pasteType=$pasteType, limit=$limit)"
}

data class SearchParams(
    val searchTerms: List<String>,
    val favorite: Boolean,
    val sort: Boolean,
    val pasteType: Int?,
    val limit: Int,
) {

    override fun toString(): String =
        "SearchParams(searchTerms=$searchTerms, favorite=$favorite, sort=$sort, pasteType=$pasteType, limit=$limit)"
}
