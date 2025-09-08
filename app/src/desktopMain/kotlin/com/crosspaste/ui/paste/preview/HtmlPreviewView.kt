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
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.ThemeDetector
import com.crosspaste.utils.getColorUtils
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.HtmlPreviewView() {
    val themeDetector = koinInject<ThemeDetector>()
    val htmlPasteItem = getPasteItem(HtmlPasteItem::class)

    val colorUtils = getColorUtils()

    val backgroundColor by remember(pasteData.id) {
        mutableStateOf(Color(htmlPasteItem.getBackgroundColor()))
    }

    val htmlBackground =
        if (backgroundColor == Color.Transparent) {
            MaterialTheme.colorScheme.background
        } else {
            backgroundColor
        }
    val isDark by remember(pasteData.id) { mutableStateOf(colorUtils.isDarkColor(backgroundColor)) }
    val richTextColor =
        if (isDark == themeDetector.isCurrentThemeDark()) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.background
        }

    SimplePreviewContentView {
        val state = rememberRichTextState()

        LaunchedEffect(htmlPasteItem.html) {
            state.setHtml(htmlPasteItem.html)
        }

        RichText(
            color = richTextColor,
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(htmlBackground)
                    .padding(tiny),
        )
    }
}
