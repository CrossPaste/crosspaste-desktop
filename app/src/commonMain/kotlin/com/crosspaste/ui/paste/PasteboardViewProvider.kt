package com.crosspaste.ui.paste

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.crosspaste.paste.PasteSingleProcess

interface PasteboardViewProvider {

    companion object {
        val previewTextStyle =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )

        val previewUrlStyle =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                textDecoration = TextDecoration.Underline,
            )
    }

    @Composable
    fun PasteShimmer(singleProcess: PasteSingleProcess?)
}
