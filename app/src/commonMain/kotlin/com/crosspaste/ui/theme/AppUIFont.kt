package com.crosspaste.ui.theme

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.base.robotoFontFamily

object AppUIFont {

    val aboutAppNameTextStyle: TextStyle
        @Composable
        get() =
            TextStyle(
                fontFamily = robotoFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )

    val aboutVersionTextStyle: TextStyle
        @Composable
        get() =
            TextStyle(
                fontFamily = robotoFontFamily(),
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            )

    val appNameTextStyle: TextStyle
        @Composable
        get() =
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = robotoFontFamily(),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )

    val companyTextStyle: TextStyle
        @Composable
        get() =
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                fontSize = 10.sp,
            )

    val countTextStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )

    val detailPasteTextStyle =
        TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 1.25.em,
        )

    val noteNameTextStyle: TextStyle =
        TextStyle(
            fontWeight = FontWeight.Light,
            fontSize = 16.sp,
            lineHeight = 1.em,
        )

    val previewAutoSize: TextAutoSize =
        TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 16.sp, stepSize = 0.2.sp)

    val pasteTextStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 1.25.em,
                lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None,
                    ),
            )

    val pasteUrlStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 1.25.em,
                textDecoration = TextDecoration.Underline,
            )

    val recommendTitleTextStyle: TextStyle =
        TextStyle(
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 1.25.em,
        )

    val recommendTextTextStyle: TextStyle =
        TextStyle(
            fontSize = 16.sp,
            lineHeight = 1.5.em,
        )

    val tokenTextStyle: TextStyle
        @Composable
        get() =
            TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )

    @Composable
    fun CustomTextFieldStyle(textAlign: TextAlign = TextAlign.Start): TextStyle {
        return TextStyle(
            textAlign = textAlign,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 1.em,
        )
    }

    @Composable
    fun SettingsTextStyle(color: Color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.settingsBackground)): TextStyle {
        return MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Light,
            fontSize = 14.sp,
            textAlign = TextAlign.Start,
            color = color,
            lineHeight = TextUnit.Unspecified,
        )
    }

    @Composable
    fun StorePathTextStyle(useDefaultStoragePath: Boolean): TextStyle {
        return TextStyle(
            textAlign = TextAlign.Start,
            color =
                if (!useDefaultStoragePath) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)
                },
            fontSize = if (!useDefaultStoragePath) 12.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 14.sp,
        )
    }
}
