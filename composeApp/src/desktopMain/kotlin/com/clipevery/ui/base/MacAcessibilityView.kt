package com.clipevery.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalExitApplication
import com.clipevery.LocalKoinApplication
import com.clipevery.app.AppRestartService
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.os.macos.api.MacosApi
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.net.URI

@Composable
fun MacAcessibilityView() {
    val current = LocalKoinApplication.current
    val exitApplication = LocalExitApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val appRestartService = current.koin.get<AppRestartService>()

    var toRestart by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val checkAccessibilityPermissions = MacosApi.INSTANCE.checkAccessibilityPermissions()
            if (checkAccessibilityPermissions) {
                toRestart = true
                break
            } else {
                delay(500)
            }
        }
    }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .wrapContentHeight()
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!toRestart) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val content = copywriter.getText("Global_Shortcut_Activation_Failed_Content")

                val click = copywriter.getText("Global_Shortcut_Activation_Failed_Click")

                val index = content.indexOf(click)

                val annotatedText =
                    buildAnnotatedString {
                        withStyle(
                            style =
                                SpanStyle(
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Light,
                                ),
                        ) {
                            append(content.substring(0, index))
                        }
                        pushStringAnnotation(tag = "clickable", annotation = "click_here")
                        withStyle(
                            style =
                                SpanStyle(
                                    color = MaterialTheme.colors.primary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Light,
                                ),
                        ) {
                            append(click)
                        }
                        pop()
                        withStyle(
                            style =
                                SpanStyle(
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Light,
                                ),
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
        } else {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = copywriter.getText("Restart_Application_Tip"),
                    color = MaterialTheme.colors.onBackground,
                    style =
                        TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.SansSerif,
                        ),
                )
                Spacer(modifier = Modifier.height(6.dp))
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
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

private fun jumpPrivacyAccessibility() {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop()
            .browse(URI("x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"))
    }
}
