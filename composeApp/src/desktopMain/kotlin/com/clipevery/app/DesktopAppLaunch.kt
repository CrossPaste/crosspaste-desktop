package com.clipevery.app

import com.clipevery.os.macos.api.MacosApi
import com.clipevery.path.DesktopPathProvider
import com.clipevery.platform.currentPlatform
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

object DesktopAppLaunch : AppLaunch, AppLock {

    private val logger: KLogger = KotlinLogging.logger {}

    private val pathProvider = DesktopPathProvider

    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    override fun acquireLock(): Pair<Boolean, Boolean> {
        val appLock = pathProvider.clipUserPath.resolve("app.lock").toFile()
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
            logger.error(e) { "Failed to create and lock file: ${e.message}" }
            return Pair(false, firstLaunch)
        }
    }

    override fun releaseLock() {
        try {
            lock?.release()
            channel?.close()
        } catch (e: Exception) {
            logger.error { "Failed to release lock: ${e.message}" }
        }
    }

    override fun launch(): AppLaunchState {
        val pair = acquireLock()
        val platform = currentPlatform()
        if (platform.isMacos()) {
            val accessibilityPermissions = MacosApi.INSTANCE.checkAccessibilityPermissions()
            return AppLaunchState(pair.first, pair.second, accessibilityPermissions)
        } else {
            return AppLaunchState(pair.first, pair.second, true)
        }
    }
}
