package com.clipevery.listen

import com.clipevery.app.AppWindowManager
import com.clipevery.clip.ClipSearchService
import com.clipevery.config.ConfigManager
import com.clipevery.utils.mainDispatcher
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val logger = KotlinLogging.logger {}

class GlobalListener(
    appWindowManager: AppWindowManager,
    configManager: ConfigManager,
    clipSearchService: ClipSearchService,
) {

    private val searchListener = SearchListener(appWindowManager, clipSearchService)

    fun start() {
        if (System.getProperty("globalListener", false.toString()).toBoolean()) {
            try {
                GlobalScreen.registerNativeHook()
                GlobalScreen.addNativeKeyListener(searchListener)
            } catch (e: NativeHookException) {
                logger.error(e) { "There was a problem registering the native hook" }
            }
        }
    }

    fun stop() {
        if (System.getProperty("globalListener", false.toString()).toBoolean()) {
            try {
                GlobalScreen.removeNativeKeyListener(searchListener)
                GlobalScreen.unregisterNativeHook()
            } catch (e: NativeHookException) {
                logger.error(e) { "There was a problem unregistering the native hook" }
            }
        }
    }
}

class SearchListener(
    private val appWindowManager: AppWindowManager,
    private val clipSearchService: ClipSearchService,
) : NativeKeyListener {

    private val logger = KotlinLogging.logger {}

    private val mainDispatcherScope = CoroutineScope(mainDispatcher)

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        val isCmdOrCtrlPressed = (e.modifiers and NativeKeyEvent.META_MASK) != 0
        val isShiftPressed = (e.modifiers and NativeKeyEvent.SHIFT_MASK) != 0
        val isSpacePressed = e.keyCode == NativeKeyEvent.VC_SPACE

        if (isCmdOrCtrlPressed && isShiftPressed && isSpacePressed) {
            logger.info { "Open search window" }
            mainDispatcherScope.launch(CoroutineName("OpenSearchWindow")) {
                clipSearchService.activeWindow()
            }
        } else if (e.keyCode == NativeKeyEvent.VC_ENTER) {
            mainDispatcherScope.launch(CoroutineName("Paste")) {
                clipSearchService.toPaste()
            }
        } else if (e.keyCode == NativeKeyEvent.VC_ESCAPE) {
            mainDispatcherScope.launch(CoroutineName("HideWindow")) {
                clipSearchService.unActiveWindow()
            }
        } else if (e.keyCode == NativeKeyEvent.VC_UP) {
            if (appWindowManager.showSearchWindow) {
                mainDispatcherScope.launch(CoroutineName("UpSelectedIndex")) {
                    clipSearchService.upSelectedIndex()
                }
            }
        } else if (e.keyCode == NativeKeyEvent.VC_DOWN) {
            if (appWindowManager.showSearchWindow) {
                mainDispatcherScope.launch(CoroutineName("DownSelectedIndex")) {
                    clipSearchService.downSelectedIndex()
                }
            }
        }
    }
}
