package com.clipevery.app

import kotlinx.serialization.Serializable

const val AppName: String = "Clipevery"

@Serializable
data class AppInfo(
    val appInstanceId: String,
    val appVersion: String,
    val userName: String
)
