package com.crosspaste.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.base.robotoFontFamily

object DesktopAppUIFont {

    val appNameTextStyle: TextStyle
        @Composable
        get() =
            TextStyle(
                color =
                    MaterialTheme.colorScheme.contentColorFor(
                        AppUIColors.appBackground,
                    ),
                fontFamily = robotoFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )

    val companyTextStyle: TextStyle
        @Composable
        get() =
            TextStyle(
                color =
                    MaterialTheme.colorScheme.contentColorFor(
                        AppUIColors.appBackground,
                    ),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                fontSize = 10.sp,
            )

    val detailPasteTextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                color =
                    MaterialTheme.colorScheme.contentColorFor(
                        DesktopAppUIColors.searchBackground,
                    ),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 1.25.em,
            )

    val keyboardCharTextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium

    @Composable
    fun StorePathTextStyle(useDefaultStoragePath: Boolean): TextStyle {
        return TextStyle(
            textAlign = TextAlign.Start,
            color =
                if (!useDefaultStoragePath) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            fontSize = if (!useDefaultStoragePath) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
        )
    }

    val tipsTextStyle: TextStyle
        @Composable
        get() =
            MaterialTheme.typography.bodySmall
}
