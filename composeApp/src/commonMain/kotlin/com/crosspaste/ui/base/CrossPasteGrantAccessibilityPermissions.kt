package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalExitApplication
import com.crosspaste.LocalKoinApplication
import com.crosspaste.app.AppRestartService
import com.crosspaste.app.ExitMode
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.CrossPasteTheme
import com.crosspaste.ui.grantPermissionColor
import kotlinx.coroutines.delay

@Composable
fun CrossPasteGrantAccessibilityPermissions(
    checkAccessibilityPermissionsFun: () -> Boolean,
    setOnTop: (Boolean) -> Unit,
) {
    val current = LocalKoinApplication.current
    val exitApplication = LocalExitApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val appRestartService = current.koin.get<AppRestartService>()
    val uiSupport = current.koin.get<UISupport>()

    var toRestart by remember { mutableStateOf(false) }

    var restarting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            if (checkAccessibilityPermissionsFun()) {
                setOnTop(true)
                toRestart = true
                break
            } else {
                delay(500)
            }
        }
    }

    CrossPasteTheme {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colors.background),
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .clip(RoundedCornerShape(6.dp))
                            .size(36.dp),
                    painter = painterResource("crosspaste_icon.png"),
                    contentDescription = "crosspaste icon",
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = copywriter.getText("permission"),
                    style =
                        TextStyle(
                            fontSize = 25.sp,
                            fontWeight = MaterialTheme.typography.h1.fontWeight,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colors.onBackground,
                        ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .wrapContentHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = copywriter.getText("absolutely_no_personal_information_is_collected_or_stored"),
                    style =
                        TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colors.onBackground,
                            textDecoration = TextDecoration.Underline,
                        ),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colors.surface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = copywriter.getText("accessibility"),
                        style =
                            TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colors.onBackground,
                                textDecoration = TextDecoration.Underline,
                            ),
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = copywriter.getText("crosspaste_needs_your_permission_to_support_global_shortcuts"),
                        style =
                            TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = FontFamily.SansSerif,
                                color = MaterialTheme.colors.onBackground,
                            ),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (!toRestart) {
                        Button(
                            modifier = Modifier.height(28.dp),
                            onClick = {
                                setOnTop(false)
                                uiSupport.jumpPrivacyAccessibility()
                            },
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, grantPermissionColor()),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(backgroundColor = grantPermissionColor()),
                            elevation =
                                ButtonDefaults.elevation(
                                    defaultElevation = 0.dp,
                                    pressedElevation = 0.dp,
                                    hoveredElevation = 0.dp,
                                    focusedElevation = 0.dp,
                                ),
                        ) {
                            Text(
                                text = copywriter.getText("grant_permission"),
                                color = Color.White,
                                style =
                                    TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Light,
                                        fontSize = 14.sp,
                                    ),
                            )
                        }
                    } else {
                        if (restarting) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        } else {
                            Button(
                                modifier = Modifier.height(28.dp),
                                onClick = {
                                    restarting = true
                                    appRestartService.restart { exitApplication(ExitMode.RESTART) }
                                },
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colors.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
                                elevation =
                                    ButtonDefaults.elevation(
                                        defaultElevation = 0.dp,
                                        pressedElevation = 0.dp,
                                        hoveredElevation = 0.dp,
                                        focusedElevation = 0.dp,
                                    ),
                            ) {
                                Text(
                                    text = copywriter.getText("restart_application"),
                                    color = Color.White,
                                    style =
                                        TextStyle(
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.Light,
                                            fontSize = 14.sp,
                                        ),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
