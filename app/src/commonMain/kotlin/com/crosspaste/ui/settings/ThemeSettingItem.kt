package com.crosspaste.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Dark_mode
import com.composables.icons.materialsymbols.rounded.Light_mode
import com.composables.icons.materialsymbols.rounded.Palette
import com.composables.icons.materialsymbols.rounded.Routine
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.tiny4X
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.ThemeDetector
import org.koin.compose.koinInject

private data class ThemeMode(
    val labelKey: String,
    val icon: ImageVector,
)

private val themeModes =
    listOf(
        ThemeMode("light", MaterialSymbols.Rounded.Light_mode),
        ThemeMode("system", MaterialSymbols.Rounded.Routine),
        ThemeMode("dark", MaterialSymbols.Rounded.Dark_mode),
    )

/**
 * Theme row: a three-position slider (sun / auto / moon) on the trailing
 * side, so the row stays as tall as its neighbours instead of growing to fit
 * three text labels.
 */
@Composable
fun ThemeSettingItem() {
    val themeDetector = koinInject<ThemeDetector>()
    val themeState = LocalThemeState.current
    val themeExt = LocalThemeExtState.current

    val selectedIndex =
        when {
            themeState.isFollowSystem -> 1
            themeState.isCurrentThemeDark -> 2
            else -> 0
        }

    SettingListItem(
        title = "theme",
        icon = IconData(MaterialSymbols.Rounded.Palette, themeExt.purpleIconColor),
        trailingContent = {
            ThemeModeSlider(selectedIndex = selectedIndex) { index ->
                when (index) {
                    0 -> themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = false)
                    1 -> themeDetector.setThemeConfig(isFollowSystem = true)
                    2 -> themeDetector.setThemeConfig(isFollowSystem = false, isUserInDark = true)
                }
            }
        },
    )
}

@Composable
private fun ThemeModeSlider(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val cellWidth: Dp = xxxxLarge - tiny4X
    val trackPadding: Dp = tiny4X
    val thumbOffset by animateDpAsState(targetValue = cellWidth * selectedIndex)

    Box(
        modifier =
            Modifier
                .height(xxLarge)
                .width(cellWidth * themeModes.size + trackPadding * 2)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(trackPadding),
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .width(cellWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
        )
        Row {
            themeModes.forEachIndexed { index, mode ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Box(
                    modifier =
                        Modifier
                            .width(cellWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = copywriter.getText(mode.labelKey),
                        modifier = Modifier.size(large),
                        tint = tint,
                    )
                }
            }
        }
    }
}
