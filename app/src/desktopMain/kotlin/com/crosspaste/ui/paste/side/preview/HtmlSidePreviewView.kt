package com.crosspaste.ui.paste.side.preview

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
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.utils.ColorAccessibility
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.HtmlSidePreviewView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteItemReader = koinInject<PasteItemReader>()
    val htmlPasteItem = getPasteItem(HtmlPasteItem::class)

    val backgroundColor by remember(pasteData.id) {
        mutableStateOf(Color(htmlPasteItem.getBackgroundColor()))
    }

    val htmlBackground =
        if (backgroundColor == Color.Transparent) {
            MaterialTheme.colorScheme.background
        } else {
            backgroundColor
        }
    val isDark by remember(pasteData.id) { mutableStateOf(ColorAccessibility.isDarkColor(htmlBackground)) }
    val richTextColor =
        if (isDark == LocalThemeState.current.isCurrentThemeDark) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.background
        }

    val charCount by remember(pasteData.id) { mutableStateOf(pasteItemReader.getText(htmlPasteItem).length) }

    SidePasteLayoutView(
        pasteBottomContent = {
            BottomGradient(
                text = copywriter.getText("character_count", "$charCount"),
                backgroundColor = htmlBackground,
            )
        },
    ) {
        val state = rememberRichTextState()

        LaunchedEffect(htmlPasteItem.hash) {
            pasteItemReader.getPreviewHtml(htmlPasteItem)?.let { state.setHtml(it) }
        }
        RichText(
            color = richTextColor,
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(htmlBackground)
                    .padding(small2X),
        )
    }
}
