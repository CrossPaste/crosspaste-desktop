package com.crosspaste.app

import com.crosspaste.path.DesktopAppPathProvider
import com.crosspaste.platform.getPlatform
import com.crosspaste.platform.macos.MacAppUtils
import com.crosspaste.platform.windows.api.User32.Companion.isInstalledFromMicrosoftStore
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

object DesktopAppLaunch : AppLaunch, AppLock {

    private val logger: KLogger = KotlinLogging.logger {}

    private val pathProvider = DesktopAppPathProvider

    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var resetLock = false

    override fun acquireLock(): Pair<Boolean, Boolean> {
        val appLock = pathProvider.pasteUserPath.resolve("app.lock").toFile()
        val firstLaunch = !appLock.exists()
        try {
            channel = FileChannel.open(appLock.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            lock = channel?.tryLock()
            if (lock == null) {
                channel?.close()
                logger.error { "Another instance of the application is already running." }
                return Pair(false, firstLaunch)
            }
            logger.info { "Application lock acquired." }

            return Pair(true, firstLaunch)
        } catch (e: OverlappingFileLockException) {
            logger.error(e) { "Another instance of the application is already running." }
            return Pair(false, firstLaunch)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create and lock file" }
            return Pair(false, firstLaunch)
        }
    }

    override fun releaseLock() {
        try {
            lock?.release()
            channel?.close()
            if (resetLock) {
                pathProvider.pasteUserPath.resolve("app.lock").toFile().delete()
                logger.info { "Application lock released and reset." }
            } else {
                logger.info { "Application lock released." }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to release lock" }
        }
    }

    override fun resetFirstLaunchFlag() {
        resetLock = true
    }

    override fun launch(): AppLaunchState {
        val pair = acquireLock()
        val platform = getPlatform()
        val pid = ProcessHandle.current().pid()
        if (platform.isMacos()) {
            val accessibilityPermissions = MacAppUtils.checkAccessibilityPermissions()
            return AppLaunchState(pid, pair.first, pair.second, accessibilityPermissions, null)
        } else if (platform.isWindows()) {
            val installFrom =
                if (isInstalledFromMicrosoftStore()) {
                    MICROSOFT_STORE
                } else {
                    null
                }
            return AppLaunchState(pid, pair.first, pair.second, true, installFrom)
        } else {
            return AppLaunchState(pid, pair.first, pair.second, true, null)
        }
    }
}
