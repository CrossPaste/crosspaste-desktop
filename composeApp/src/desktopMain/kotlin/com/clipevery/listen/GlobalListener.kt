package com.clipevery.listen

import com.clipevery.app.AppEnv
import com.clipevery.config.ConfigManager
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import io.github.oshai.kotlinlogging.KotlinLogging

val logger = KotlinLogging.logger {}

class GlobalListener(configManager: ConfigManager) {

    init {
        if (configManager.config.appEnv == AppEnv.DEVELOPMENT) {
            try {
                GlobalScreen.registerNativeHook()
            } catch (ex: NativeHookException) {
                logger.error { "There was a problem registering the native hook." }
                logger.error { "ex.message" }
            }
            GlobalScreen.addNativeKeyListener(GlobalKeyListenerExample())
        }
    }

}


class GlobalKeyListenerExample : NativeKeyListener {
    override fun nativeKeyPressed(e: NativeKeyEvent) {
        logger.info { "Key Pressed: " + NativeKeyEvent.getKeyText(e.keyCode) }
        if (e.keyCode == NativeKeyEvent.VC_ESCAPE) {
            try {
                GlobalScreen.unregisterNativeHook()
            } catch (nativeHookException: NativeHookException) {
                nativeHookException.printStackTrace()
            }
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent) {
        logger.info { "Key Released: " + NativeKeyEvent.getKeyText(e.keyCode) }
    }

    override fun nativeKeyTyped(e: NativeKeyEvent) {
        logger.info { "Key Typed: " + NativeKeyEvent.getKeyText(e.keyCode) }
    }
}