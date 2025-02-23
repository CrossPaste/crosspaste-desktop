package com.crosspaste.secure

import kotlinx.coroutines.sync.Mutex

data class SecureSession(
    val mutex: Mutex = Mutex(),
    var processor: SecureMessageProcessor? = null,
)
