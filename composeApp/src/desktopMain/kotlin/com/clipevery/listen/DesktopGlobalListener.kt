package com.clipevery.listen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalExitApplication
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppRestartService
import com.clipevery.app.AppWindowManager
import com.clipevery.clip.ClipSearchService
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.listener.GlobalListener
import com.clipevery.ui.base.ComposeMessageViewFactory
import com.clipevery.ui.base.MessageType
import com.clipevery.utils.getSystemProperty
import com.clipevery.utils.mainDispatcher
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

val logger = KotlinLogging.logger {}

class DesktopGlobalListener(
    appWindowManager: AppWindowManager,
    clipSearchService: ClipSearchService,
) : GlobalListener {

    private val systemProperty = getSystemProperty()

    private val searchListener = SearchListener(appWindowManager, clipSearchService)

    override var errorCode: Int? by mutableStateOf(null)

    private val messageFactory = GlobalListenerMessageViewFactory()

    override fun isRegistered(): Boolean {
        return GlobalScreen.isNativeHookRegistered()
    }

    override fun start() {
        if (systemProperty.get("globalListener", false.toString()).toBoolean()) {
            try {
                if (!isRegistered()) {
                    GlobalScreen.registerNativeHook()
                    GlobalScreen.addNativeKeyListener(searchListener)
                }
            } catch (e: NativeHookException) {
                errorCode = e.code
                logger.error(e) { "There was a problem registering the native hook" }
            }
        }
    }

    override fun stop() {
        if (systemProperty.get("globalListener", false.toString()).toBoolean()) {
            try {
                if (isRegistered()) {
                    GlobalScreen.removeNativeKeyListener(searchListener)
                    GlobalScreen.unregisterNativeHook()
                }
            } catch (e: NativeHookException) {
                logger.error(e) { "There was a problem unregistering the native hook" }
            }
        }
    }

    override fun getComposeMessageViewFactory(): ComposeMessageViewFactory {
        return messageFactory
    }
}

private class GlobalListenerMessageViewFactory : ComposeMessageViewFactory {

    override var showMessage: Boolean by mutableStateOf(true)

    private var jumpPrivacyAccessibility: Boolean by mutableStateOf(false)

    @Composable
    override fun MessageView(key: Any) {
        if (key == NativeHookException.DARWIN_AXAPI_DISABLED) {
            GlobalShortcutActivationFailedMessageView()
        }
    }

    private fun jumpPrivacyAccessibility() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop()
                .browse(URI("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"))
        }
    }

    @Composable
    fun GlobalShortcutActivationFailedMessageView() {
        val current = LocalKoinApplication.current
        val appWindowManager = current.koin.get<AppWindowManager>()
        val exitApplication = LocalExitApplication.current
        val globalListener = current.koin.get<GlobalListener>()
        val copywriter = current.koin.get<GlobalCopywriter>()
        val appRestartService = current.koin.get<AppRestartService>()

        val messageStyle = MessageType.Error.getMessageStyle()

        LaunchedEffect(appWindowManager.showMainWindow) {
            if (appWindowManager.showMainWindow) {
                if (globalListener.isRegistered()) {
                    showMessage = false
                } else {
                    globalListener.errorCode?.let { code ->
                        if (code == NativeHookException.DARWIN_AXAPI_DISABLED && !jumpPrivacyAccessibility) {
                            delay(8000) // wait to read the message
                            jumpPrivacyAccessibility()
                            jumpPrivacyAccessibility = true
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = copywriter.getText("Global_Shortcut_Activation_Failed"),
                modifier = Modifier.wrapContentWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = messageStyle.messageColor,
                style =
                    TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.SansSerif,
                    ),
            )
            Spacer(modifier = Modifier.weight(1f))

            Icon(
                modifier =
                    Modifier.clickable {
                        showMessage = false
                    },
                painter = painterResource("icon/toast/close.svg"),
                contentDescription = "Cancel",
                tint = messageStyle.messageColor,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            val content = copywriter.getText("Global_Shortcut_Activation_Failed_Content")

            val click = copywriter.getText("Global_Shortcut_Activation_Failed_Click")

            val index = content.indexOf(click)

            val annotatedText =
                buildAnnotatedString {
                    withStyle(
                        style = SpanStyle(color = MaterialTheme.colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Light),
                    ) {
                        append(content.substring(0, index))
                    }
                    pushStringAnnotation(tag = "clickable", annotation = "click_here")
                    withStyle(style = SpanStyle(color = MaterialTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Light)) {
                        append(click)
                    }
                    pop()
                    withStyle(
                        style = SpanStyle(color = MaterialTheme.colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Light),
                    ) {
                        append(content.substring(index + click.length))
                    }
                }

            ClickableText(
                text = annotatedText,
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "clickable", start = offset, end = offset)
                        .firstOrNull()?.let {
                            if (it.item == "click_here") {
                                jumpPrivacyAccessibility()
                            }
                        }
                },
            )
        }

        if (jumpPrivacyAccessibility) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = Modifier.wrapContentWidth().height(28.dp),
                    border = BorderStroke(1.dp, Color(0xFFAFCBE1)),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    elevation =
                        ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            focusedElevation = 0.dp,
                        ),
                    onClick = {
                        appRestartService.restart { exitApplication() }
                    },
                ) {
                    Text(
                        text = copywriter.getText("Restart_Application"),
                        style =
                            TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                            ),
                        color = MaterialTheme.colors.onBackground,
                    )
                }
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
