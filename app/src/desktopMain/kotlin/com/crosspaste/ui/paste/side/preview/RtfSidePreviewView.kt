package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.utils.ColorAccessibility
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import org.koin.compose.koinInject

@OptIn(ExperimentalRichTextApi::class)
@Composable
fun PasteDataScope.RtfSidePreviewView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteItemReader = koinInject<PasteItemReader>()
    val rtfPasteItem = getPasteItem(RtfPasteItem::class)

    val backgroundColor =
        remember(pasteData.id, rtfPasteItem.hash) {
            Color(rtfPasteItem.getBackgroundColor())
        }

    val themeBackground = MaterialTheme.colorScheme.background
    val rtfBackground =
        if (backgroundColor == Color.Transparent) {
            themeBackground
        } else {
            backgroundColor.compositeOver(themeBackground)
        }
    val isDark =
        remember(rtfBackground) {
            ColorAccessibility.isDarkColor(rtfBackground)
        }
    val richTextColor =
        if (isDark == LocalThemeState.current.isCurrentThemeDark) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.background
        }

    val charCount =
        remember(pasteData.id, rtfPasteItem.hash) {
            pasteItemReader.getText(rtfPasteItem).length
        }

    SidePasteLayoutView(
        pasteBottomContent = {
            BottomGradient(
                text = copywriter.getText("character_count", "$charCount"),
                backgroundColor = rtfBackground,
            )
        },
    ) {
        val state = remember(pasteData.id) { RichTextState() }

        LaunchedEffect(pasteData.id, rtfPasteItem.hash) {
            pasteItemReader.getPreviewHtml(rtfPasteItem)?.let { state.setHtml(it) }
        }

        RichText(
            color = richTextColor,
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rtfBackground)
                    .padding(small2X),
        )
    }
}
