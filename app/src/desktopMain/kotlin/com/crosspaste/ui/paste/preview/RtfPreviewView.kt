package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.getColorUtils
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText

@Composable
fun PasteDataScope.RtfPreviewView() {
    val rtfPasteItem = getPasteItem(RtfPasteItem::class)

    val colorUtils = getColorUtils()

    val backgroundColor by remember(pasteData.id) {
        mutableStateOf(Color(rtfPasteItem.getBackgroundColor()))
    }

    val rtfBackground =
        if (backgroundColor == Color.Transparent) {
            MaterialTheme.colorScheme.background
        } else {
            backgroundColor
        }
    val isDark by remember(pasteData.id) { mutableStateOf(colorUtils.isDarkColor(rtfBackground)) }
    val richTextColor =
        if (isDark == LocalThemeState.current.isCurrentThemeDark) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.background
        }

    SimplePreviewContentView {
        val state = rememberRichTextState()

        LaunchedEffect(rtfPasteItem.getHtml()) {
            state.setHtml(rtfPasteItem.getHtml())
        }

        RichText(
            color = richTextColor,
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rtfBackground)
                    .padding(tiny),
        )
    }
}
