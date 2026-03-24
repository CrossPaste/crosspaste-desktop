package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class MobilePromoteConfig(
    val promote: MobilePromote = MobilePromote(),
)

@Serializable
data class MobilePromote(
    val ios: MobileAppPromote = MobileAppPromote(),
    val android: MobileAppPromote = MobileAppPromote(),
    val domestic: MobileAppPromote = MobileAppPromote(),
)

@Serializable
data class MobileAppPromote(
    val enabled: Boolean = false,
    val url: String = "",
)

interface MobilePromoteService {

    val config: StateFlow<MobilePromoteConfig>

    fun start()

    fun stop()
}
