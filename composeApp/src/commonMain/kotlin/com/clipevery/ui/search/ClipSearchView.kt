package com.clipevery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipSearchService
import com.clipevery.ui.ClipeveryTheme
import org.koin.core.KoinApplication
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener


fun createSearchWindow(clipSearchService: ClipSearchService,
                       koinApplication: KoinApplication) {
    val appUI = clipSearchService.getAppUI()
    if (clipSearchService.tryStart()) {
        application {

            val windowState = rememberWindowState(
                placement = WindowPlacement.Floating,
                position = WindowPosition.Aligned(Alignment.Center),
                size = DpSize(800.dp, 80.dp)
            )

            Window(
                onCloseRequest = ::exitApplication,
                visible = appUI.showSearchWindow,
                state = windowState,
                title = "Clipevery Search",
                alwaysOnTop = true,
                undecorated = true,
                transparent = true,
                resizable = false
                ) {

                LaunchedEffect(Unit) {
                    window.addWindowFocusListener(object : WindowFocusListener {
                        override fun windowGainedFocus(e: WindowEvent?) {
                            appUI.showSearchWindow = true
                        }

                        override fun windowLostFocus(e: WindowEvent?) {
                            appUI.showSearchWindow = false
                        }
                    })

                }

                ClipeveryAppSearchView(
                    koinApplication,
                    hideWindow = { appUI.showSearchWindow = false }
                )
            }
        }
    } else {
        appUI.showSearchWindow = true
    }
}

@Composable
fun ClipeveryAppSearchView(koinApplication: KoinApplication,
                            hideWindow: () -> Unit,
                           ) {
    CompositionLocalProvider(
        LocalKoinApplication provides koinApplication,
    ) {
        ClipeverySearchWindow(hideWindow)
    }
}

@Composable
fun ClipeverySearchWindow(hideWindow: () -> Unit) {

    var inputSearch by remember { mutableStateOf("") }


    ClipeveryTheme {
        Box(modifier = Modifier
            .background(Color.Transparent)
            .clip(RoundedCornerShape(10.dp))
            .width(800.dp)
            .height(80.dp)
            .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier
                .shadow(5.dp, RoundedCornerShape(10.dp))
                .width(780.dp)
                .height(60.dp)
                .background(MaterialTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                Row {
                    TextField(
                        modifier = Modifier.fillMaxSize(),
                        value = inputSearch,
                        onValueChange = { inputSearch = it },
                        placeholder = { Text(
                            modifier = Modifier.wrapContentSize(),
                            text = "please input search",
                            style = TextStyle(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                                fontSize = 15.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        },
                        isError = false,
                        singleLine = true,
                        colors = TextFieldDefaults.textFieldColors(
                            textColor = MaterialTheme.colors.onBackground,
                            disabledTextColor = Color.Transparent,
                            backgroundColor = Color.Transparent,
                            cursorColor = MaterialTheme.colors.primary,
                            errorCursorColor = Color.Red,
                            focusedIndicatorColor = MaterialTheme.colors.primary,
                            unfocusedIndicatorColor = MaterialTheme.colors.secondaryVariant,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        textStyle =  TextStyle(
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colors.onBackground,
                            fontSize = 15.sp,
                            lineHeight = 5.sp
                        )
                    )
                }
            }
        }
    }
}