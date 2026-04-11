package com.crosspaste.app

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromoteConfig(
    val promote: Promote = Promote(),
)

@Serializable
data class Promote(
    val ios: AppPromote = AppPromote(),
    val android: AndroidPromote = AndroidPromote(),
)

@Serializable
data class AndroidPromote(
    @SerialName("google-play")
    val googlePlay: AppPromote = AppPromote(),
    val domestic: DomesticPromote = DomesticPromote(),
)

@Serializable
data class AppPromote(
    val enabled: Boolean = false,
    val url: String = "",
)

@Serializable
data class DomesticPromote(
    val enabled: Boolean = false,
    val path: String = "",
)

interface PromoteService {

    val config: StateFlow<PromoteConfig>

    fun start()

    fun stop()
}
