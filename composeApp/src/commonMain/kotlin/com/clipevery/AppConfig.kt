package com.clipevery

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(val bindingState: Boolean = false)
