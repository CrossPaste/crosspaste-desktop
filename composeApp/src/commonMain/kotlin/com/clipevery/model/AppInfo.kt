package com.clipevery.model

const val AppName: String = "Clipevery"

data class AppInfo(
    val appInstanceId: String,
    val appVersion: String,
    val userName: String
)
