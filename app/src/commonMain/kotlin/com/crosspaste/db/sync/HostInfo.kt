package com.crosspaste.db.sync

import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val networkPrefixLength: Short,
    var hostAddress: String
)
