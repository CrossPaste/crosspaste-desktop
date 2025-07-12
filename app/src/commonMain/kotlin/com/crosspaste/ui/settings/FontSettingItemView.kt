package com.crosspaste.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.window.Popup
import com.crosspaste.ui.base.FontManager
import com.crosspaste.ui.base.MenuItemView
import com.crosspaste.ui.base.arrowDown
import com.crosspaste.ui.base.arrowLeft
import com.crosspaste.ui.base.arrowRight
import com.crosspaste.ui.base.arrowUp
import com.crosspaste.ui.base.rememberUserSelectedFont
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.getFontWidth
import com.crosspaste.ui.theme.AppUIFont.menuItemTextStyle
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tiny3XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FontSettingItemView() {
    val density = LocalDensity.current

    val fontManager = koinInject<FontManager>()

    var hasBeenClicked by remember { mutableStateOf(false) }
    var showMoreFont by remember { mutableStateOf(false) }

    var fontOnDismissTime by remember { mutableStateOf(0L) }

    var animationPhase by remember { mutableStateOf(0) }

    val fontArrow: Painter =
        when (animationPhase) {
            0 -> arrowDown()
            1 -> arrowLeft()
            2 -> arrowUp()
            3 -> arrowRight()
            else -> arrowDown()
        }

    LaunchedEffect(showMoreFont, hasBeenClicked) {
        if (hasBeenClicked) {
            animationPhase = (animationPhase + 1) % 4
            delay(100) // delay for the intermediate phase (arrowLeft)
            animationPhase = (animationPhase + 1) % 4
        }
    }

    SettingItemView(
        // painter = font(),
        text = "font",
    ) {
        val allPossibleFonts = fontManager.selectableFonts
        val currentFont by rememberUserSelectedFont()
        Row(
            modifier =
                Modifier
                    .wrapContentSize()
                    .combinedClickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = {
                            val currentTimeMillis = nowEpochMilliseconds()
                            if (currentTimeMillis - fontOnDismissTime > 500) {
                                showMoreFont = !showMoreFont
                                hasBeenClicked = true
                            }
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsText(text = currentFont.name)

            Icon(
                modifier =
                    Modifier
                        .size(large2X),
                painter = fontArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (showMoreFont) {
            Popup(
                alignment = Alignment.TopEnd,
                offset =
                    IntOffset(
                        with(density) { (large2X).roundToPx() },
                        with(density) { (xxLarge).roundToPx() },
                    ),
                onDismissRequest = {
                    if (showMoreFont) {
                        showMoreFont = false
                        fontOnDismissTime = nowEpochMilliseconds()
                    }
                },
            ) {
                val scrollState = rememberScrollState()
                val isScrolling = scrollState.isScrollInProgress
                Box(
                    modifier =
                        Modifier
                            .wrapContentHeight()
                            .heightIn(max = 300.dp)
                            .wrapContentWidth()
                            .background(Color.Transparent)
                            .clip(tiny2XRoundedCornerShape)
                            .shadow(small),
                ) {
                    val maxWidth =
                        max(
                            gigantic,
                            getFontWidth(allPossibleFonts.map { it.name }),
                        )

                    Column(
                        modifier =
                            Modifier
                                .width(maxWidth)
                                .verticalScroll(scrollState)
                                .background(MaterialTheme.colorScheme.surface),
                    ) {
                        allPossibleFonts.forEachIndexed { _, fontInfo ->
                            MenuItemView(
                                fontInfo.name,
                                textStyle =
                                    menuItemTextStyle.copy(
                                        fontFamily = fontInfo.fontFamily,
                                    ),
                            ) {
                                fontManager.setFont(fontInfo.uri)
                                showMoreFont = false
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier =
                            Modifier
                                .background(color = Color.Transparent)
                                .fillMaxHeight()
                                .padding(end = tiny3X)
                                .align(Alignment.CenterEnd),
                        adapter = rememberScrollbarAdapter(scrollState = scrollState),
                        style =
                            ScrollbarStyle(
                                minimalHeight = medium,
                                thickness = tiny,
                                shape = tiny3XRoundedCornerShape,
                                hoverDurationMillis = 300,
                                unhoverColor =
                                    if (isScrolling) {
                                        MaterialTheme.colorScheme
                                            .contentColorFor(
                                                AppUIColors.appBackground,
                                            ).copy(alpha = 0.48f)
                                    } else {
                                        Color.Transparent
                                    },
                                hoverColor =
                                    MaterialTheme.colorScheme.contentColorFor(
                                        AppUIColors.appBackground,
                                    ),
                            ),
                    )
                }
            }
        }
    }
}
