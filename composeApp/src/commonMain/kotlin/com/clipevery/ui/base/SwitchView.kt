package com.clipevery.ui.base

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun CustomSwitch(checked: Boolean,
                 onCheckedChange: (Boolean) -> Unit,
                 modifier: Modifier = Modifier,
                 checkedThumbColor: Color = MaterialTheme.colors.primary,
                 uncheckedThumbColor: Color = MaterialTheme.colors.onSurface) {
    Switch(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = checkedThumbColor,
            checkedTrackColor = checkedThumbColor.copy(alpha = 0.5f),
            uncheckedThumbColor = uncheckedThumbColor,
            uncheckedTrackColor = uncheckedThumbColor.copy(alpha = 0.5f)
        )
    )
}