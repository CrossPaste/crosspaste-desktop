package com.crosspaste.ui.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.crosspaste.app.AppName
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.app.ExitMode
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.platform.macos.api.LeftClickCallback
import com.crosspaste.platform.macos.api.MacosApi
import com.crosspaste.platform.macos.api.MenuCallback
import com.crosspaste.ui.LocalExitApplication
import com.crosspaste.ui.base.MenuHelper
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

object MacTrayView {

    val logger = KotlinLogging.logger {}

    @Composable
    fun Tray() {
        val applicationExit = LocalExitApplication.current
        val appWindowManager = koinInject<DesktopAppWindowManager>()
        val configManager = koinInject<CommonConfigManager>()
        val menuHelper = koinInject<MenuHelper>()

        var trayManager by remember { mutableStateOf<NativeTrayManager?>(null) }

        LaunchedEffect(Unit) {
            trayManager =
                NativeTrayManager(
                    appWindowManager,
                    configManager,
                    menuHelper,
                    applicationExit,
                )
        }

        DisposableEffect(Unit) {
            onDispose {
                try {
                    trayManager?.cleanup()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to cleanup tray manager" }
                }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NativeTrayManager(
    appWindowManager: DesktopAppWindowManager,
    configManager: CommonConfigManager,
    menuHelper: MenuHelper,
    applicationExit: (ExitMode) -> Unit,
) {
    private val lib = MacosApi.INSTANCE

    private val menuScope: CoroutineScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private var menuCallbacks = mutableMapOf<Int, () -> Unit>()

    val bytes =
        Thread
            .currentThread()
            .contextClassLoader
            .getResourceAsStream("crosspaste-mac-tray.png")
            ?.readBytes() ?: ByteArray(0)

    private val callback: MenuCallback =
        MenuCallback { itemId ->
            menuCallbacks[itemId]?.invoke()
        }

    private val leftClickCallback =
        LeftClickCallback {
            appWindowManager.hideMainWindow()
            if (appWindowManager.showSearchWindow.value) {
                appWindowManager.hideSearchWindow()
            } else {
                menuScope.launch {
                    appWindowManager.recordActiveInfoAndShowSearchWindow(
                        useShortcutKeys = false,
                    )
                }
            }
        }

    init {
        lib.traySetCallback(callback)
        val success = lib.trayInit(bytes, bytes.size, AppName, leftClickCallback)
        if (!success) {
            throw RuntimeException("Failed to initialize tray")
        }
        menuScope.launch {
            var isFirst = true
            configManager.config
                .map { it.language }
                .distinctUntilChanged()
                .collect {
                    menuHelper.createMacMenu(this@NativeTrayManager, applicationExit, !isFirst)
                    isFirst = false
                }
        }
    }

    fun addMenuItem(
        itemId: Int,
        title: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): Int {
        menuCallbacks[itemId] = onClick
        lib.trayAddMenuItem(itemId, title, enabled)
        return itemId
    }

    fun addSeparator() {
        lib.trayAddSeparator()
    }

    fun updateMenuItem(
        itemId: Int,
        title: String,
        enabled: Boolean = true,
    ) {
        lib.trayUpdateMenuItem(itemId, title, enabled)
    }

    fun cleanup() {
        lib.trayCleanup()
        menuCallbacks.clear()
    }
}
