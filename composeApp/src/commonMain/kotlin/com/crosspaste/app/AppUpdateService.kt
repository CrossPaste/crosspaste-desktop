package com.crosspaste.app

import io.github.z4kn4fein.semver.Version

interface AppUpdateService {

    var currentVersion: Version

    var lastVersion: Version?

    fun checkForUpdate()

    fun jumpDownload()

    fun existNewVersion(): Boolean

    fun start()

    fun stop()
}
