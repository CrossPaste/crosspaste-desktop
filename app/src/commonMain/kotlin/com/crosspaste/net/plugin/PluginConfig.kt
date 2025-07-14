package com.crosspaste.net.plugin

import com.crosspaste.secure.SecureStore
import io.ktor.utils.io.*

@KtorDsl
class PluginConfig(
    val secureStore: SecureStore,
)
