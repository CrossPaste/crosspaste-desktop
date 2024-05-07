package com.clipevery.build

data class JbrReleases(
    var jbr: Map<String, JbrDetails> = mutableMapOf(),
)

data class JbrDetails(
    var url: String = "",
    var sha512: String = "",
)