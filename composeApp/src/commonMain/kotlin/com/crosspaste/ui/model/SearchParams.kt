package com.crosspaste.ui.model

data class SearchParams(
    val searchTerms: List<String>,
    val favorite: Boolean,
    val sort: Boolean,
    val pasteType: Int?,
    val limit: Int,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchParams) return false

        if (searchTerms != other.searchTerms) return false
        if (favorite != other.favorite) return false
        if (sort != other.sort) return false
        if (pasteType != other.pasteType) return false
        if (limit != other.limit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = searchTerms.hashCode()
        result = 31 * result + favorite.hashCode()
        result = 31 * result + sort.hashCode()
        result = 31 * result + (pasteType ?: 0)
        result = 31 * result + limit
        return result
    }

    override fun toString(): String {
        return "SearchParams(searchTerms=$searchTerms, favorite=$favorite, sort=$sort, pasteType=$pasteType, limit=$limit)"
    }
}
