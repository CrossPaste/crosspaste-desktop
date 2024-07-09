package com.crosspaste.listen

import com.crosspaste.app.AppLaunchState
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.listener.GlobalListener
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.Toast
import com.crosspaste.ui.base.ToastManager
import com.crosspaste.utils.getSystemProperty
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.NativeHookException.DARWIN_AXAPI_DISABLED
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import io.github.oshai.kotlinlogging.KotlinLogging

val logger = KotlinLogging.logger {}

class DesktopGlobalListener(
    private val appLaunchState: AppLaunchState,
    private val shortcutKeysListener: NativeKeyListener,
    private val mouseListener: NativeMouseListener,
    private val toastManager: ToastManager,
    private val copywriter: GlobalCopywriter,
) : GlobalListener {

    private val systemProperty = getSystemProperty()

    override fun isRegistered(): Boolean {
        return GlobalScreen.isNativeHookRegistered()
    }

    override fun start() {
        if (systemProperty.get("globalListener", false.toString()).toBoolean()) {
            try {
                if (appLaunchState.accessibilityPermissions && !isRegistered()) {
                    GlobalScreen.registerNativeHook()
                    GlobalScreen.addNativeKeyListener(shortcutKeysListener)
                    GlobalScreen.addNativeMouseListener(mouseListener)
                } else {
                    grantAccessibilityPermissions()
                }
            } catch (e: NativeHookException) {
                if (e.code == DARWIN_AXAPI_DISABLED) {
                    grantAccessibilityPermissions()
                } else {
                    toastManager.setToast(
                        Toast(
                            messageType = MessageType.Error,
                            message =
                                "${copywriter.getText("failed_to_register_keyboard_listener")}. " +
                                    "${copywriter.getText("error_Code")} ${e.code}",
                        ),
                    )
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
            try {
                if (isRegistered()) {
                    GlobalScreen.removeNativeKeyListener(shortcutKeysListener)
                    GlobalScreen.removeNativeMouseListener(mouseListener)
                    GlobalScreen.unregisterNativeHook()
                }
            } catch (e: NativeHookException) {
                logger.error(e) { "There was a problem unregistering the native hook" }
            }
        }
    }
}
