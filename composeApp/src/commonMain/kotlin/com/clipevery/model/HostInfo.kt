package com.clipevery.model

import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val displayName: String,
    val hostAddress: String)
