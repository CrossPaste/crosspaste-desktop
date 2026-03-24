package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class PromoteConfig(
    val promote: Promote = Promote(),
)

@Serializable
data class Promote(
    val ios: AppPromote = AppPromote(),
    val android: AppPromote = AppPromote(),
    val domestic: AppPromote = AppPromote(),
    val desktop: AppPromote = AppPromote(),
)

@Serializable
data class AppPromote(
    val enabled: Boolean = false,
    val url: String = "",
)

interface PromoteService {

    val config: StateFlow<PromoteConfig>

    fun start()

    fun stop()
}
