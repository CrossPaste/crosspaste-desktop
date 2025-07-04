package com.crosspaste.ui.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.menuItemTextStyle
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero

@Composable
fun MenuItemView(
    text: String,
    textStyle: TextStyle = menuItemTextStyle,
    background: Color = AppUIColors.menuBackground,
    paddingValues: PaddingValues = PaddingValues(horizontal = medium, vertical = zero),
    enabledInteraction: Boolean = true,
    extendContent: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val backgroundColor =
        if (enabledInteraction && isHovered) {
            AppUIColors.selectedMenuBackground
        } else {
            background
        }

    var modifier =
        Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .hoverable(interactionSource = interactionSource)
            .background(backgroundColor)

    if (enabledInteraction) {
        modifier = modifier.clickable(onClick = onClick)
    }

    Row(
        modifier =
            modifier.height(xxLarge)
                .padding(paddingValues),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.contentColorFor(backgroundColor),
            style = textStyle,
        )

        extendContent?.let {
            it()
        }
    }
}
