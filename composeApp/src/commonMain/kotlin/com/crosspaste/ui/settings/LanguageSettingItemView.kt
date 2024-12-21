package com.crosspaste.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.MenuItem
import com.crosspaste.ui.base.arrowDown
import com.crosspaste.ui.base.arrowLeft
import com.crosspaste.ui.base.arrowRight
import com.crosspaste.ui.base.arrowUp
import com.crosspaste.ui.base.getMenWidth
import com.crosspaste.ui.base.language
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LanguageSettingItemView() {
    val density = LocalDensity.current

    val copywriter = koinInject<GlobalCopywriter>()

    var hasBeenClicked by remember { mutableStateOf(false) }
    var showMoreLanguage by remember { mutableStateOf(false) }

    var languagePosition by remember { mutableStateOf(Offset.Zero) }
    var languageSize by remember { mutableStateOf(Size(0.0f, 0.0f)) }
    var languageOnDismissTime by remember { mutableStateOf(0L) }

    var animationPhase by remember { mutableStateOf(0) }

    val languageArrow: Painter =
        when (animationPhase) {
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

    SettingItemView(
        painter = language(),
        text = "language",
        tint = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier =
                Modifier.wrapContentSize()
                    .combinedClickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {
                            val currentTimeMillis = Clock.System.now().toEpochMilliseconds()
                            if (currentTimeMillis - languageOnDismissTime > 500) {
                                showMoreLanguage = !showMoreLanguage
                                hasBeenClicked = true
                            }
                        },
                    ).onGloballyPositioned { coordinates ->
                        languagePosition = coordinates.localToWindow(Offset.Zero)
                        languageSize = coordinates.size.toSize()
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsText(copywriter.getText("current_language"))

            Icon(
                modifier =
                    Modifier
                        .size(20.dp),
                painter = languageArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (showMoreLanguage) {
            Popup(
                alignment = Alignment.TopEnd,
                offset =
                    IntOffset(
                        with(density) { ((20).dp).roundToPx() },
                        with(density) { (30.dp).roundToPx() },
                    ),
                onDismissRequest = {
                    if (showMoreLanguage) {
                        showMoreLanguage = false
                        languageOnDismissTime = Clock.System.now().toEpochMilliseconds()
                    }
                },
            ) {
                Box(
                    modifier =
                        Modifier
                            .wrapContentSize()
                            .background(Color.Transparent)
                            .shadow(15.dp),
                ) {
                    val maxWidth =
                        max(
                            150.dp,
                            getMenWidth(copywriter.getAllLanguages().map { it.name }.toTypedArray()),
                        )

                    Column(
                        modifier =
                            Modifier
                                .width(maxWidth)
                                .wrapContentHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.surface),
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
}
