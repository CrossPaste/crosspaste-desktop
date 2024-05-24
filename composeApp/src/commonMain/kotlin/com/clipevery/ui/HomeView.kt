package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clipevery.LocalExitApplication
import com.clipevery.LocalKoinApplication
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.ClipIconButton
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.getMenWidth
import com.clipevery.ui.base.settings
import java.awt.Desktop
import java.net.URI

@Composable
fun HomeView(currentPageViewContext: MutableState<PageViewContext>) {
    HomeWindowDecoration(currentPageViewContext)
    TabsView(currentPageViewContext)
}

@Composable
fun HomeWindowDecoration(currentPage: MutableState<PageViewContext>) {
    TitleView(currentPage)
}

val customFontFamily =
    FontFamily(
        Font(resource = "font/BebasNeue.otf", FontWeight.Normal),
    )

@Preview
@Composable
fun TitleView(currentPage: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val applicationExit = LocalExitApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    var showPopup by remember { mutableStateOf(false) }

    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    var buttonSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size(0.0f, 0.0f)) }

    val density = LocalDensity.current

    Box(
        modifier =
            Modifier.background(Color(0xFF121314))
                .border(0.dp, Color.Transparent)
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.Transparent,
                                ),
                            startY = 0.0f,
                            endY = 3.0f,
                        ),
                ),
    ) {
        Row(
            modifier =
                Modifier.align(Alignment.CenterStart)
                    .wrapContentWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                modifier =
                    Modifier.padding(13.dp, 13.dp, 13.dp, 13.dp)
                        .align(Alignment.CenterVertically)
                        .clip(RoundedCornerShape(3.dp))
                        .size(36.dp),
                painter = painterResource("clipevery_icon.png"),
                contentDescription = "clipevery icon",
            )
            Column(
                Modifier.wrapContentWidth()
                    .align(Alignment.CenterVertically)
                    .offset(y = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    modifier = Modifier.align(Alignment.Start),
                    text = "Compile Future",
                    color = Color.White,
                    style = TextStyle(fontWeight = FontWeight.Light),
                    fontSize = 11.sp,
                )
                Text(
                    modifier = Modifier.align(Alignment.Start),
                    text = "Clipevery",
                    color = Color.White,
                    fontSize = 25.sp,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    fontFamily = customFontFamily,
                )
            }
            Column(
                Modifier.fillMaxWidth()
                    .align(Alignment.CenterVertically)
                    .offset(y = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ClipIconButton(
                    radius = 18.dp,
                    onClick = {
                        showPopup = !showPopup
                    },
                    modifier =
                        Modifier
                            .padding(13.dp)
                            .align(Alignment.End)
                            .background(Color.Transparent, CircleShape)
                            .onGloballyPositioned { coordinates ->
                                buttonPosition = coordinates.localToWindow(Offset.Zero)
                                buttonSize = coordinates.size.toSize()
                            },
                ) {
                    Icon(
                        settings(),
                        contentDescription = "info",
                        modifier = Modifier.padding(3.dp).size(30.dp),
                        tint = Color.White,
                    )
                }

                if (showPopup) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset =
                            IntOffset(
                                with(density) { ((-14).dp).roundToPx() },
                                with(density) { (60.dp).roundToPx() },
                            ),
                        onDismissRequest = {
                            if (showPopup) {
                                showPopup = false
                            }
                        },
                        properties =
                            PopupProperties(
                                focusable = true,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = true,
                            ),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .wrapContentSize()
                                    .background(Color.Transparent)
                                    .shadow(15.dp),
                        ) {
                            val menuTexts =
                                arrayOf(
                                    copywriter.getText("Check_for_updates"),
                                    copywriter.getText("Settings"),
                                    copywriter.getText("ShortcutKeys"),
                                    copywriter.getText("About"),
                                    copywriter.getText("FQA"),
                                    copywriter.getText("Quit"),
                                )

                            val maxWidth = max(150.dp, getMenWidth(menuTexts))

                            Column(
                                modifier =
                                    Modifier
                                        .width(maxWidth)
                                        .wrapContentHeight()
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(MaterialTheme.colors.surface),
                            ) {
                                MenuItem(copywriter.getText("Check_for_updates")) {
                                    // TODO: check for updates
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("Settings")) {
                                    currentPage.value = PageViewContext(PageViewType.SETTINGS, currentPage.value)
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("Shortcut_Keys")) {
                                    currentPage.value = PageViewContext(PageViewType.SHORTCUT_KEYS, currentPage.value)
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("About")) {
                                    currentPage.value = PageViewContext(PageViewType.ABOUT, currentPage.value)
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("FQA")) {
                                    Desktop.getDesktop().browse(URI("https://www.clipevery.com/FQA"))
                                    showPopup = false
                                }
                                Divider()
                                MenuItem(copywriter.getText("Quit")) {
                                    showPopup = false
                                    applicationExit()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
