package com.crosspaste.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.zero

object AppUIFont {
    val mediumBodyTextStyle: TextStyle
        @Composable
        get() =
            MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Light,
                lineHeight = 1.25.em,
            )

    val emptyScreenTipsTextStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            MaterialTheme.typography.titleLarge.copy(
                textAlign = TextAlign.Center,
            )

    val generalBodyTextStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium

    val imageResolutionTextStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
            )

    val menuItemTextStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Light,
                lineHeight = 1.em,
                lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
            )

    val pasteTextStyle: TextStyle
        @Composable @ReadOnlyComposable
        get() =
            TextStyle(
                color =
                    MaterialTheme.colorScheme.contentColorFor(
                        AppUIColors.pasteBackground,
                    ),
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

    val previewAutoSize: TextAutoSize =
        TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = 16.sp, stepSize = 0.2.sp)

    val propertyTextStyle: TextStyle
        @Composable
        get() = MaterialTheme.typography.labelMedium

    val settingItemsTitleTextStyle: TextStyle
        @Composable
        get() =
            MaterialTheme.typography.titleSmall.copy(
                color =
                    MaterialTheme.colorScheme.contentColorFor(
                        AppUIColors.topBackground,
                    ),
            )

    val bottomSolidTitleTextStyle
        @Composable @ReadOnlyComposable
        get() =
            MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )

    val tipsTextStyle: TextStyle
        @Composable
        get() =
            MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                lineHeight = 1.em,
                lineHeightStyle =
                    LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both,
                    ),
            )

    @Composable
    fun getFontWidth(
        textList: List<String>,
        textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
        paddingValues: PaddingValues = PaddingValues(horizontal = medium, vertical = zero),
        extendFunction: (Int) -> Dp = { zero },
    ): Dp {
        var maxWidth = zero
        textList.forEachIndexed { index, text ->
            maxWidth = maxOf(maxWidth, measureTextWidth(text, textStyle) + extendFunction(index))
        }
        return maxWidth +
            paddingValues.calculateLeftPadding(LayoutDirection.Ltr) +
            paddingValues.calculateRightPadding(LayoutDirection.Ltr)
    }

    @Composable
    fun NumberTextStyle(textAlign: TextAlign = TextAlign.Start): TextStyle =
        TextStyle(
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            lineHeight = 1.em,
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            textAlign = textAlign,
        )

    @Composable
    fun SettingsTextStyle(
        color: Color =
            MaterialTheme.colorScheme.contentColorFor(
                AppUIColors.generalBackground,
            ),
    ): TextStyle =
        MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Light,
            fontSize = 14.sp,
            textAlign = TextAlign.Start,
            color = color,
            lineHeight = 1.em,
            lineHeightStyle =
                LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
        )
}
