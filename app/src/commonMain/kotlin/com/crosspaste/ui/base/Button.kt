package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import com.crosspaste.ui.theme.AppUISize.zeroButtonElevation

@Composable
fun SettingButton(
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        modifier = Modifier.height(xxLarge),
        onClick = onClick,
        shape = tinyRoundedCornerShape,
        colors = colors,
        border = BorderStroke(tiny5X, MaterialTheme.colorScheme.surfaceDim),
        contentPadding = PaddingValues(horizontal = tiny, vertical = zero),
        elevation = zeroButtonElevation,
    ) {
        content()
    }
}

@Composable
fun SettingOutlineButton(
    onClick: () -> Unit,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        modifier = Modifier.height(xxLarge),
        onClick = onClick,
        shape = tinyRoundedCornerShape,
        colors = colors,
        border = BorderStroke(tiny5X, MaterialTheme.colorScheme.surfaceDim),
        contentPadding = PaddingValues(horizontal = tiny, vertical = zero),
        elevation = zeroButtonElevation,
    ) {
        content()
    }
}
