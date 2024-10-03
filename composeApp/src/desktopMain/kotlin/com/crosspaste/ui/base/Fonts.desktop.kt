package com.crosspaste.ui.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.crosspaste.composeapp.generated.resources.Res
import com.crosspaste.composeapp.generated.resources.Subset_RobotoCondensed_Medium
import org.jetbrains.compose.resources.Font

@Composable
actual fun robotoFontFamily(): FontFamily {
    return FontFamily(
        Font(Res.font.Subset_RobotoCondensed_Medium),
    )
}
