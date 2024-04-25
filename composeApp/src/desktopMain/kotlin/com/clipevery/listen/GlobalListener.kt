package com.clipevery.listen

import com.clipevery.Dependencies
import com.clipevery.app.AppUI
import com.clipevery.clip.ClipSearchService
import com.clipevery.config.ConfigManager
import com.clipevery.ui.search.createSearchWindow
import com.clipevery.utils.ioDispatcher
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
    appUI: AppUI,
    configManager: ConfigManager,
    clipSearchService: ClipSearchService,
) {

    init {
        if (System.getProperty("supportShortcutKey", false.toString()).toBoolean()) {
            try {
                GlobalScreen.registerNativeHook()
            } catch (ex: NativeHookException) {
                logger.error { "There was a problem registering the native hook." }
                logger.error { "ex.message" }
            }
            GlobalScreen.addNativeKeyListener(OpenSearchListener(appUI, clipSearchService))
        }
    }
}

class OpenSearchListener(
    private val appUI: AppUI,
    private val clipSearchService: ClipSearchService,
) : NativeKeyListener {

    private val logger = KotlinLogging.logger {}

    private val dispatcher = CoroutineScope(ioDispatcher)

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        val isCmdOrCtrlPressed = (e.modifiers and NativeKeyEvent.META_MASK) != 0
        val isShiftPressed = (e.modifiers and NativeKeyEvent.SHIFT_MASK) != 0
        val isSpacePressed = e.keyCode == NativeKeyEvent.VC_SPACE

        if (isCmdOrCtrlPressed && isShiftPressed && isSpacePressed) {
            dispatcher.launch(CoroutineName("CrateSearchWindow")) {
                logger.info { "Open search window" }
                createSearchWindow(clipSearchService, Dependencies.koinApplication)
            }
        }

        if (e.keyCode == NativeKeyEvent.VC_ESCAPE) {
            dispatcher.launch(CoroutineName("HideWindow")) {
                clipSearchService.unActiveWindow()
            }
        } else if (e.keyCode == NativeKeyEvent.VC_UP) {
            if (appUI.showSearchWindow) {
                clipSearchService.upSelectedIndex()
            }
        } else if (e.keyCode == NativeKeyEvent.VC_DOWN) {
            if (appUI.showSearchWindow) {
                clipSearchService.downSelectedIndex()
            }
        }
    }
}
