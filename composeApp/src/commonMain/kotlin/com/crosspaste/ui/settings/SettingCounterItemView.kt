package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.crosspaste.ui.base.Counter

@Composable
fun SettingCounterItemView(
    text: String,
    painter: Painter,
    unit: String,
    rule: (Long) -> Boolean,
    getCurrentCounterValue: () -> Long,
    onChange: (Long) -> Unit,
) {
    SettingItemView(
        painter = painter,
        text = text,
        tint = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            modifier =
                Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Counter(defaultValue = getCurrentCounterValue(), unit = unit, rule = rule) {
                onChange(it)
            }
        }
    }
}
