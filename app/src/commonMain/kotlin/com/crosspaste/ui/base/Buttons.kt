package com.crosspaste.ui.base

import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.xxLarge

@Composable
expect fun GeneralIconButton(
    imageVector: ImageVector,
    desc: String,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    iconColor: Color = LocalContentColor.current,
    buttonSize: Dp = xxLarge,
    iconSize: Dp = large,
    onClick: () -> Unit,
)
