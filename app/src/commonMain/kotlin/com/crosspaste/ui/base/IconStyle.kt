package com.crosspaste.ui.base

interface IconStyle {

    fun isMacStyleIcon(
        source: String,
        appInstanceId: String? = null,
    ): Boolean

    fun refreshStyle(
        source: String,
        appInstanceId: String? = null,
    )
}
