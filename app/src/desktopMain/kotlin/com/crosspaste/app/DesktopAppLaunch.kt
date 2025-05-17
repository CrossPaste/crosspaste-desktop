package com.crosspaste.app

import com.crosspaste.path.AppPathProvider
import com.crosspaste.platform.Platform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.api.User32.Companion.isInstalledFromMicrosoftStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

class DesktopAppLaunch(
    private val platform: Platform,
    private val appPathProvider: AppPathProvider,
) : AppLaunch, AppLock {

    private val logger: KLogger = KotlinLogging.logger {}

    private val _appLaunchState =
        MutableStateFlow<AppLaunchState>(
            DesktopAppLaunchState(-1, false, false, false, null),
        )

    override val appLaunchState: StateFlow<AppLaunchState> = _appLaunchState

    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var resetLock = false

    override fun acquireLock(): AppLockState {
        val appLock = appPathProvider.pasteUserPath.resolve("app.lock").toFile()
        val firstLaunch = !appLock.exists()
        return runCatching {
            channel = FileChannel.open(appLock.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            lock = channel?.tryLock()
            if (lock == null) {
                channel?.close()
                logger.error { "Another instance of the application is already running." }
                GeneralAppLockState(false, firstLaunch)
            } else {
                logger.info { "Application lock acquired." }

                GeneralAppLockState(true, firstLaunch)
            }
        }.getOrElse { e ->
            when (e) {
                is OverlappingFileLockException -> {
                    logger.error(e) { "Another instance of the application is already running." }
                    GeneralAppLockState(false, firstLaunch)
                }
                else -> {
                    logger.error { "Failed to create and lock file" }
                    GeneralAppLockState(false, firstLaunch)
                }
            }
        }
    }

    override fun releaseLock() {
        runCatching {
            lock?.release()
            channel?.close()
            if (resetLock) {
                appPathProvider.pasteUserPath.resolve("app.lock").toFile().delete()
                logger.info { "Application lock released and reset." }
            } else {
                logger.info { "Application lock released." }
            }
        }.onFailure { e ->
            logger.error(e) { "Failed to release lock" }
        }
    }

    override fun resetFirstLaunchFlag() {
        resetLock = true
    }

    override suspend fun launch(): DesktopAppLaunchState {
        val appLockState = acquireLock()
        val pid = ProcessHandle.current().pid()
        val acquiredLock = appLockState.acquiredLock
        val firstLaunch = appLockState.firstLaunch
        val appLaunchState: DesktopAppLaunchState =
            if (platform.isMacos()) {
                val accessibilityPermissions = MacAppUtils.checkAccessibilityPermissions()
                DesktopAppLaunchState(
                    pid,
                    acquiredLock,
                    firstLaunch,
                    accessibilityPermissions,
                    null,
                )
            } else if (platform.isWindows()) {
                val path = appPathProvider.pasteAppPath.toNioPath()
                val installFrom =
                    if (isInstalledFromMicrosoftStore(path)) {
                        MICROSOFT_STORE
                    } else {
                        null
                    }
                DesktopAppLaunchState(pid, acquiredLock, firstLaunch, true, installFrom)
            } else {
                DesktopAppLaunchState(pid, acquiredLock, firstLaunch, true, null)
            }
        _appLaunchState.value = appLaunchState
        return appLaunchState
    }
}
