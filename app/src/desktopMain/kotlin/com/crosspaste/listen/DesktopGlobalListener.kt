package com.crosspaste.listen

import com.crosspaste.app.DesktopAppLaunchState
import com.crosspaste.listener.GlobalListener
import com.crosspaste.notification.MessageType
import com.crosspaste.notification.NotificationManager
import com.crosspaste.utils.getSystemProperty
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.NativeHookException.DARWIN_AXAPI_DISABLED
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import io.github.oshai.kotlinlogging.KotlinLogging

class DesktopGlobalListener(
    private val appLaunchState: DesktopAppLaunchState,
    private val shortcutKeysListener: NativeKeyListener,
    private val mouseListener: NativeMouseListener,
    private val notificationManager: NotificationManager,
) : GlobalListener {

    private val logger = KotlinLogging.logger {}

    private val systemProperty = getSystemProperty()

    override fun isRegistered(): Boolean {
        return GlobalScreen.isNativeHookRegistered()
    }

    override fun start() {
        if (systemProperty.get("globalListener", false.toString()).toBoolean()) {
            runCatching {
                if (appLaunchState.accessibilityPermissions && !isRegistered()) {
                    GlobalScreen.registerNativeHook()
                    GlobalScreen.addNativeKeyListener(shortcutKeysListener)
                    GlobalScreen.addNativeMouseListener(mouseListener)
                } else {
                    grantAccessibilityPermissions()
                }
            }.onFailure { e ->
                if (e is NativeHookException) {
                    if (e.code == DARWIN_AXAPI_DISABLED) {
                        grantAccessibilityPermissions()
                    } else {
                        notificationManager.sendNotification(
                            title = { it.getText("failed_to_register_keyboard_listener") },
                            message = { "${it.getText("error_Code")} ${e.code}" },
                            messageType = MessageType.Error,
                        )
                    }
                }
                logger.error(e) { "There was a problem registering the native hook" }
            }
        }
    }

    private fun grantAccessibilityPermissions() {
        appLaunchState.accessibilityPermissions = false
    }

    override fun stop() {
        if (systemProperty.get("globalListener", false.toString()).toBoolean()) {
            runCatching {
                if (isRegistered()) {
                    GlobalScreen.removeNativeKeyListener(shortcutKeysListener)
                    GlobalScreen.removeNativeMouseListener(mouseListener)
                    GlobalScreen.unregisterNativeHook()
                }
            }.onFailure { e ->
                logger.error(e) { "There was a problem unregistering the native hook" }
            }
        }
    }
}
