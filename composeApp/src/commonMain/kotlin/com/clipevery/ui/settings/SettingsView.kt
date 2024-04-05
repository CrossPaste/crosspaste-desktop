package com.clipevery.ui.settings

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
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import com.clipevery.LocalKoinApplication
import com.clipevery.config.ConfigManager
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.WindowDecoration
import com.clipevery.ui.base.CustomSwitch
import com.clipevery.ui.base.MenuItem
import com.clipevery.ui.base.arrowDown
import com.clipevery.ui.base.arrowLeft
import com.clipevery.ui.base.arrowRight
import com.clipevery.ui.base.arrowUp
import com.clipevery.ui.base.getMenWidth
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val configManager = current.koin.get<ConfigManager>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    var hasBeenClicked by remember { mutableStateOf(false) }
    var showMoreLanguage by remember { mutableStateOf(false) }

    var animationPhase by remember { mutableStateOf(0) }

    var languagePosition by remember { mutableStateOf(Offset.Zero) }
    var languageSize by remember { mutableStateOf(Size(0.0f, 0.0f)) }
    var languageOnDismissTime by remember { mutableStateOf(0L) }


    val languageArrow: Painter = when (animationPhase) {
        0 -> arrowDown()
        1 -> arrowLeft()
        2 -> arrowUp()
        3 -> arrowRight()
        else -> arrowDown()
    }

    val density = LocalDensity.current

    LaunchedEffect(showMoreLanguage, hasBeenClicked) {
        if (hasBeenClicked) {
            animationPhase = (animationPhase + 1) % 4
            delay(100) // delay for the intermediate phase (arrowLeft)
            animationPhase = (animationPhase + 1) % 4
        }
    }

    WindowDecoration(currentPageViewContext, "Settings")


    Column(modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colors.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(25.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            settingsText("${copywriter.getText("Language")}:")
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
                settingsText(copywriter.getText("CurrentLanguage"))

                Icon(
                    modifier = Modifier
                        .padding(5.dp, 0.dp, 5.dp, 0.dp)
                        .size(15.dp),
                    painter = languageArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colors.onBackground
                )
            }

            if (showMoreLanguage) {
                Popup(
                    offset = IntOffset(
                        with(density) { ((20).dp).roundToPx() },
                        with(density) { (30.dp).roundToPx() },
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

                        val maxWidth = max(150.dp,
                            getMenWidth(copywriter.getAllLanguages().map { it.name }.toTypedArray())
                        )

                        Column(
                            modifier = Modifier
                                .width(maxWidth)
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colors.surface)
                        ) {
                            val allLanguages = copywriter.getAllLanguages()
                            allLanguages.forEachIndexed { _, language ->
                                MenuItem(language.name) {
                                    copywriter.switchLanguage(language.abridge)
                                    showMoreLanguage = false
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(25.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            settingsText(copywriter.getText("Theme"))
            Row(Modifier.padding(5.dp, 0.dp, 0.dp, 0.dp)) {
                ThemeSegmentedControl()
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(25.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            var isEncrypted by remember { mutableStateOf(configManager.config.isEncryptSync) }
            CustomSwitch(
                modifier = Modifier.width(32.dp)
                    .height(20.dp),
                checked = isEncrypted,
                onCheckedChange = { it ->
                    isEncrypted = it
                    configManager.updateConfig { it.copy(isEncryptSync = isEncrypted) }
                }
            )

            Spacer(modifier = Modifier.width(10.dp))

            settingsText(copywriter.getText("Encrypted_sync"))
        }

        Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(25.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            var isChecked by remember { mutableStateOf(false) }
            // TODO: Boot_start_up
            CustomSwitch(
                modifier = Modifier.width(32.dp)
                    .height(20.dp),
                checked = isChecked,
                onCheckedChange = { isChecked = it }
            )

            Spacer(modifier = Modifier.width(10.dp))

            settingsText(copywriter.getText("Boot_start_up"))
        }

        Row(modifier = Modifier.fillMaxWidth().height(40.dp).padding(25.dp, 5.dp, 0.dp, 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            var isChecked by remember { mutableStateOf(false) }
            // TODO: AutomaticUpdate
            CustomSwitch(
                modifier = Modifier.width(32.dp)
                    .height(20.dp),
                checked = isChecked,
                onCheckedChange = { isChecked = it }
            )

            Spacer(modifier = Modifier.width(10.dp))

            settingsText(copywriter.getText("AutomaticUpdate"))
        }

        Spacer(modifier = Modifier.height(10.dp))

        SettingsItemView("Network") {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(text = copywriter.getText("Network"),
                    color = MaterialTheme.colors.onBackground,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(fontWeight = FontWeight.Light)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        SettingsItemView("Store") {
            StoreSettingsView()
        }
    }
}

@Composable
fun settingsText(text: String) {
    Text(text = text,
        color = MaterialTheme.colors.onBackground,
        fontSize = 14.sp,
        fontFamily = FontFamily.SansSerif,
        style = TextStyle(fontWeight = FontWeight.Light)
    )
}