package com.clipevery.listen

import androidx.compose.ui.unit.dp
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.listener.GlobalListener
import com.clipevery.ui.base.ClipDialog
import com.clipevery.ui.base.DialogService
import com.clipevery.ui.base.MacAcessibilityView
import com.clipevery.ui.base.MessageType
import com.clipevery.ui.base.Toast
import com.clipevery.ui.base.ToastManager
import com.clipevery.utils.getSystemProperty
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.NativeHookException.DARWIN_AXAPI_DISABLED
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import com.github.kwhat.jnativehook.mouse.NativeMouseListener
import io.github.oshai.kotlinlogging.KotlinLogging

val logger = KotlinLogging.logger {}

class DesktopGlobalListener(
    private val shortcutKeysListener: NativeKeyListener,
    private val mouseListener: NativeMouseListener,
    private val dialogService: DialogService,
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
                if (!isRegistered()) {
                    GlobalScreen.registerNativeHook()
                    GlobalScreen.addNativeKeyListener(shortcutKeysListener)
                    GlobalScreen.addNativeMouseListener(mouseListener)
                }
            } catch (e: NativeHookException) {
                if (e.code == DARWIN_AXAPI_DISABLED) {
                    dialogService.pushDialog(
                        ClipDialog(
                            key = e.code,
                            title = "Global_Shortcut_Activation_Failed",
                            width = 320.dp,
                        ) {
                            MacAcessibilityView()
                        },
                    )
                } else {
                    toastManager.setToast(
                        Toast(
                            messageType = MessageType.Error,
                            message =
                                "${copywriter.getText("Failed_to_register_keyboard_listener")}. " +
                                    "${copywriter.getText("Error_Code")} ${e.code}",
                        ),
                    )
                }
                logger.error(e) { "There was a problem registering the native hook" }
            }
        }
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
