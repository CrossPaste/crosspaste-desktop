package com.crosspaste.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.navigation.compose.currentBackStackEntryAsState
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Chevron_left
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WindowDecoration() {
    val copywriter = koinInject<GlobalCopywriter>()

    val appSizeValue = LocalDesktopAppSizeValueState.current
    val navController = LocalNavHostController.current

    val backStackEntry by navController.currentBackStackEntryAsState()

    val canNavigateBack = navController.previousBackStackEntry != null

    val routeName =
        backStackEntry?.let { getRouteName(it.destination) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(appSizeValue.windowDecorationHeight)
                .padding(horizontal = medium)
                .offset(y = -appSizeValue.windowDecorationHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()

        val animatedStartPadding by animateDpAsState(
            targetValue = if (isHovered && canNavigateBack) tiny else zero,
            label = "startPaddingAnimation",
        )

        val backgroundColor =
            if (isHovered && canNavigateBack) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            }

        Box(
            modifier =
                Modifier
                    .height(xxxxLarge)
                    .wrapContentWidth()
                    .clip(tinyRoundedCornerShape)
                    .background(backgroundColor)
                    .run {
                        if (canNavigateBack) {
                            this
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        navController.popBackStack()
                                    },
                                )
                        } else {
                            this
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(vertical = tiny2X)
                        .padding(start = animatedStartPadding, end = medium)
                        .wrapContentSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canNavigateBack) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Chevron_left,
                        contentDescription = "back",
                        modifier = Modifier.size(xLarge),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )

                    if (routeName != null) {
                        Spacer(modifier = Modifier.size(tiny3X))
                    }
                }

                if (routeName != null) {
                    Text(
                        modifier = Modifier,
                        text = copywriter.getText(routeName),
                        color =
                            MaterialTheme.colorScheme.contentColorFor(
                                AppUIColors.appBackground,
                            ),
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                lineHeight = 1.em,
                                lineHeightStyle =
                                    LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                            ),
                    )
                }
            }
        }
    }
}
