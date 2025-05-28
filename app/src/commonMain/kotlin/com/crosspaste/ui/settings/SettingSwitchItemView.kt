package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.theme.AppUISize.large2X

@Composable
fun SettingSwitchItemView(
    text: String,
    painter: Painter,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    getCurrentSwitchValue: () -> Boolean,
    onChange: (Boolean) -> Unit,
) {
    SettingItemView(
        painter = painter,
        text = text,
        tint = tint,
    ) {
        CustomSwitch(
            modifier =
                Modifier.width(32.dp)
                    .height(large2X),
            checked = getCurrentSwitchValue(),
            onCheckedChange = {
                onChange(it)
            },
        )
    }
}
