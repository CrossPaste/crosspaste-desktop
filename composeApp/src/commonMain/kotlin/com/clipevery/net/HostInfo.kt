package com.clipevery.net

import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val displayName: String,
    val hostAddress: String)
