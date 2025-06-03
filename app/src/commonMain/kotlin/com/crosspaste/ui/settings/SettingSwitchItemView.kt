package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium

@Composable
fun SettingSwitchItemView(
    text: String,
    isFinalText: Boolean = false,
    painter: Painter? = null,
    tint: Color =
        MaterialTheme.colorScheme.contentColorFor(
            AppUIColors.generalBackground,
        ),
    getCurrentSwitchValue: () -> Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingItemView(
        isFinalText = isFinalText,
        painter = painter,
        text = text,
        tint = tint,
    ) {
        CustomSwitch(
            modifier =
                Modifier.width(medium * 2)
                    .height(large2X),
            checked = getCurrentSwitchValue(),
            onCheckedChange = {
                onChange(it)
            },
        )
    }
}
