package com.clipevery.net

import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val hostName: String,
    val hostAddress: String)
