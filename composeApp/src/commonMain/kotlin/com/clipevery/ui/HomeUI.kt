package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import com.clipevery.LocalKoinApplication
import com.clipevery.PageType
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.loadImageBitmap
import compose.icons.TablerIcons
import compose.icons.tablericons.Settings
import java.awt.Desktop
import java.net.URI
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@Composable
fun HomeUI(currentPage: MutableState<PageType>) {
    HomeWindowDecoration(currentPage)
    TabsUI()
}

@Composable
fun HomeWindowDecoration(currentPage: MutableState<PageType>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        color = MaterialTheme.colors.background,
        shape = RoundedCornerShape(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        )
    ) {
        TitleUI(currentPage)
    }
}


@Preview
@Composable
fun TitleUI(currentPage: MutableState<PageType>) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    var showPopup by remember { mutableStateOf(false) }
    var onDismissTime by remember { mutableStateOf(0L) }

    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    var buttonSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size(0.0f, 0.0f)) }


    val customFontFamily = FontFamily(
        Font(resource = "font/BebasNeue.otf", FontWeight.Normal)
    )

    Box(
        modifier = Modifier.background(Color.Black)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    startY = 0.0f,
                    endY = 3.0f
                )
            ),
    ) {
        Row(modifier = Modifier.align(Alignment.CenterStart)
            .wrapContentWidth(),
            horizontalArrangement = Arrangement.Center) {
            Image(
                modifier = Modifier.padding(13.dp, 13.dp, 13.dp, 13.dp)
                    .align(Alignment.CenterVertically)
                    .clip(RoundedCornerShape(3.dp))
                    .size(36.dp),
                bitmap = loadImageBitmap("clipevery_icon.png"),
                contentDescription = "clipevery icon",
            )
            Column(Modifier.wrapContentWidth()
                .align(Alignment.CenterVertically)
                .offset(y = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(modifier = Modifier.align(Alignment.Start),
                    text = "Compile Future",
                    color = Color.White,
                    style = TextStyle(fontWeight = FontWeight.Light),
                    fontSize = 11.sp,
                    )
                Text(modifier = Modifier.align(Alignment.Start),
                    text = "Clipevery",
                    color = Color.White,
                    fontSize = 25.sp,
                    style = TextStyle(fontWeight = FontWeight.Bold),
                    fontFamily = customFontFamily,
                    )
            }
            Column(Modifier.fillMaxWidth()
                .align(Alignment.CenterVertically)
                .offset(y = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {


                IconButton(
                    onClick = {
                        val currentTimeMillis = System.currentTimeMillis()
                        if (currentTimeMillis - onDismissTime >= 500 && !showPopup) {
                            showPopup = true
                        }
                    },
                    modifier = Modifier.padding(13.dp)
                        .align(Alignment.End)
                        .size(36.dp) // Set the size of the button
                        .background(Color.Black, CircleShape) // Set the background to blue and shape to circle
                        .onGloballyPositioned { coordinates ->
                            buttonPosition = coordinates.localToWindow(Offset.Zero)
                            buttonSize = coordinates.size.toSize()
                        }

                ) {
                    // Icon inside the IconButton
                    Icon(modifier = Modifier.padding(3.dp)
                        .size(30.dp),
                        imageVector = TablerIcons.Settings,
                        contentDescription = "Settings",
                        tint = Color.White)
                }

                if (showPopup) {
                    Popup(
                        offset = IntOffset(
                            220.dp.value.toInt(),
                            (buttonPosition.y.dp + buttonSize.height.dp - 10.dp).value.roundToInt()
                        ),
                        onDismissRequest = {
                            if (showPopup) {
                                showPopup = false
                                onDismissTime = System.currentTimeMillis()
                            }
                        }
                    ) {
                        Box(modifier = Modifier
                            .wrapContentSize()
                            .background(Color.Transparent)
                            .shadow(15.dp)) {
                            Column(
                                modifier = Modifier
                                    .width(180.dp)
                                    .wrapContentHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.White)
                            ) {
                                MenuItem(copywriter.getText("Check_for_updates")) {
                                    // TODO: check for updates
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("Settings")) {
                                    currentPage.value = PageType.SETTINGS
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("About")) {
                                    currentPage.value = PageType.ABOUT
                                    showPopup = false
                                }
                                MenuItem(copywriter.getText("FQA")) {
                                    Desktop.getDesktop().browse(URI("https://www.clipevery.com/FQA"))
                                    showPopup = false
                                }
                                Divider()
                                MenuItem(copywriter.getText("Quit")) {
                                    showPopup = false
                                    exitProcess(0)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor = if (isHovered) Color(0xFFE8F5FF) else Color.Transparent

    Text(
        text = text,
        fontSize = 12.sp,
        fontFamily = FontFamily.SansSerif,
        style = TextStyle(fontWeight = FontWeight.Light),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource = interactionSource)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp, 8.dp, 16.dp, 8.dp),
    )
}