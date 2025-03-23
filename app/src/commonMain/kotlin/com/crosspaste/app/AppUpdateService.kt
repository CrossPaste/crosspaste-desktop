package com.crosspaste.app

import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AppUpdateService {

    val currentVersion: StateFlow<Version>

    val lastVersion: StateFlow<Version?>

    fun checkForUpdate()

    fun jumpDownload()

    fun existNewVersion(): Flow<Boolean>

    fun start()

    fun stop()
}
