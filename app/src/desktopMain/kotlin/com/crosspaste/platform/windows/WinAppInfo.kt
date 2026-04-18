package com.crosspaste.platform.windows

import com.crosspaste.app.AppInfo
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.utils.getFileUtils
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.sun.jna.platform.win32.WinDef.HWND
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.Path

class WinAppInfo(
    val hwnd: HWND,
) {

    companion object {
        fun createWinAppInfo(hwnd: HWND): WinAppInfo = WinAppInfo(hwnd)
    }

    fun getThreadId(caches: WinAppInfoCaches): Int? = caches.getThreadId(hwnd)

    fun getExeFilePath(caches: WinAppInfoCaches): Path? = caches.getExeFilePath(hwnd)

    fun getAppName(caches: WinAppInfoCaches): String? = caches.getAppName(hwnd)
}

class WinAppInfoCaches(
    private val appInfo: AppInfo,
    private val userDataPathProvider: UserDataPathProvider,
    private val scope: CoroutineScope,
) {

    private val logger = KotlinLogging.logger {}

    private val fileUtils = getFileUtils()

    private val threadIdCache: LoadingCache<HWND, Int?> =
        Caffeine
            .newBuilder()
            .maximumSize(100)
            .build { hwnd ->
                runCatching {
                    WindowsProcessUtils.getThreadId(hwnd)
                }.getOrNull()
            }

    private val exeFilePathCache: LoadingCache<HWND, Path?> =
        Caffeine
            .newBuilder()
            .maximumSize(100)
            .build { hwnd ->
                runCatching {
                    WindowsProcessUtils.getExeFilePath(hwnd)
                }.getOrNull()
            }

    private val appNameCache: LoadingCache<HWND, String?> =
        Caffeine
            .newBuilder()
            .maximumSize(100)
            .build { hwnd ->
                runCatching {
                    exeFilePathCache.get(hwnd)?.let { path ->
                        WindowsProcessUtils.getFileDescription(path)?.let { appName ->
                            scope.launch {
                                saveAppImage(path, appName)
                            }
                            appName
                        }
                    }
                }.getOrNull()
            }

    @Synchronized
    private fun saveAppImage(
        exeFilePath: Path,
        appName: String,
    ) {
        runCatching {
            val iconPath = userDataPathProvider.resolveIconPath(appInfo.appInstanceId, appName)
            if (!fileUtils.existFile(iconPath)) {
                WindowsIconUtils.extractAndSaveIcon(exeFilePath, iconPath)
            }
        }.onFailure { e ->
            logger.warn(e) { "Failed to save app icon for $appName" }
        }
    }

    fun getThreadId(hwnd: HWND): Int? = threadIdCache.get(hwnd)

    fun getExeFilePath(hwnd: HWND): Path? = exeFilePathCache.get(hwnd)

    fun getAppName(hwnd: HWND): String? = appNameCache.get(hwnd)
}
