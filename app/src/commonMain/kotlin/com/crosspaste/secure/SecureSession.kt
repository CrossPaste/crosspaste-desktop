package com.crosspaste.secure

import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile

data class SecureSession(
    val mutex: Mutex = Mutex(),
    @Volatile var processor: SecureMessageProcessor? = null,
)
