package com.crosspaste.sync

import com.crosspaste.net.VersionRelation

data class ResolveCallback(
    val updateVersionRelation: (VersionRelation) -> Unit,
    val onComplete: () -> Unit,
)
