package com.clipevery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import com.clipevery.LocalKoinApplication
import com.clipevery.PageType
import com.clipevery.i18n.GlobalCopywriter
import kotlinx.coroutines.delay

@Composable
fun SettingsUI(currentPage: MutableState<PageType>) {
    SettingsContentUI(currentPage)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsContentUI(currentPage: MutableState<PageType>) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    var hasBeenClicked by remember { mutableStateOf(false) }
    var showMoreLanguage by remember { mutableStateOf(false) }

    var animationPhase by remember { mutableStateOf(0) }

    var languagePosition by remember { mutableStateOf(Offset.Zero) }
    var languageSize by remember { mutableStateOf(Size(0.0f, 0.0f)) }
    var languageOnDismissTime by remember { mutableStateOf(0L) }


    val languageArrow: ImageVector = when (animationPhase) {
        0 -> arrowDown()
        1 -> arrowLeft()
        2 -> arrowUp()
        3 -> arrowRight()
        else -> arrowDown()
    }

    LaunchedEffect(showMoreLanguage, hasBeenClicked) {
        if (hasBeenClicked) {
            animationPhase = (animationPhase + 1) % 4
            delay(100) // delay for the intermediate phase (arrowLeft)
            animationPhase = (animationPhase + 1) % 4
        }
    }

    WindowDecoration(currentPage, "Settings")


    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(25.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${copywriter.getText("Language")}:",
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light))
            Row(modifier = Modifier.padding(6.dp).wrapContentSize()
                .combinedClickable(interactionSource = MutableInteractionSource(),
                    indication = null,
                    onClick = {
                        val currentTimeMillis = System.currentTimeMillis()
                        if (currentTimeMillis - languageOnDismissTime > 500) {
                            showMoreLanguage = !showMoreLanguage
                            hasBeenClicked = true
                        }
                    }
                ).onGloballyPositioned { coordinates ->
                    languagePosition = coordinates.localToWindow(Offset.Zero)
                    languageSize = coordinates.size.toSize()
                },
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = copywriter.getText("CurrentLanguage"),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(fontWeight = FontWeight.Light)
                )
                Icon(
                    modifier = Modifier
                        .padding(5.dp, 0.dp, 5.dp, 0.dp)
                        .size(15.dp),
                    imageVector = languageArrow,
                    contentDescription = null,
                    tint = Color.Black
                )
            }

            if (showMoreLanguage) {
                Popup(
                    offset = IntOffset(
                        20,
                        70
                    ),
                    onDismissRequest = {
                        if (showMoreLanguage) {
                            showMoreLanguage = false
                            languageOnDismissTime = System.currentTimeMillis()
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
                            val allLanguages = copywriter.getAllLanguages()
                            allLanguages.forEachIndexed { _, language ->
                                MenuItem(language.name) {
                                    copywriter.switchLanguage(language.abridge)
                                    showMoreLanguage = false
                                    currentPage.value = PageType.HOME
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(25.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${copywriter.getText("Theme")}:",
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light))
            Row(Modifier.padding(5.dp, 0.dp, 0.dp, 0.dp)) {
                ThemeSegmentedControl()
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(20.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            var isChecked by remember { mutableStateOf(false) }
            // TODO: Boot_start_up
            Switch(
                checked = isChecked,
                onCheckedChange = { isChecked = it }
            )

            Text(text = copywriter.getText("Boot_start_up"),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light))
        }

        Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(20.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            var isChecked by remember { mutableStateOf(false) }
            // TODO: AutomaticUpdate
            Switch(
                checked = isChecked,
                onCheckedChange = { isChecked = it }
            )

            Text(text = copywriter.getText("AutomaticUpdate"),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                style = TextStyle(fontWeight = FontWeight.Light))
        }

        Spacer(modifier = Modifier.height(10.dp))

        SettingsItemUI("Network") {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = copywriter.getText("Network"),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(fontWeight = FontWeight.Light)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        SettingsItemUI("Store") {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = copywriter.getText("Store"),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(fontWeight = FontWeight.Light)
                )
            }
        }
    }
}