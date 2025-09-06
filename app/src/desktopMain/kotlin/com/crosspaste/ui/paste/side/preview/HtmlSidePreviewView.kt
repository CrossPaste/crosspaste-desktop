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
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.ThemeDetector
import com.crosspaste.utils.getColorUtils
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.HtmlSidePreviewView() {
    val colorUtils = getColorUtils()
    getPasteItem(HtmlPasteItem::class).let { htmlPasteItem ->
        val copywriter = koinInject<GlobalCopywriter>()
        val themeDetector = koinInject<ThemeDetector>()
        val text = htmlPasteItem.getText()
        val backgroundColorValue by remember(pasteData.id) {
            mutableStateOf(htmlPasteItem.getBackgroundColor())
        }
        val backgroundColor =
            backgroundColorValue?.let {
                val color = Color(it)
                if (color == Color.Transparent) {
                    MaterialTheme.colorScheme.background
                } else {
                    color
                }
            } ?: MaterialTheme.colorScheme.background
        val isDark by remember(pasteData.id) { mutableStateOf(colorUtils.isDarkColor(backgroundColor)) }
        val richTextColor =
            if (isDark == themeDetector.isCurrentThemeDark()) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.background
            }
        SidePasteLayoutView(
            pasteBottomContent = {
                BottomGradient(
                    text = copywriter.getText("character_count", "${text.length}"),
                    backgroundColor = backgroundColor,
                )
            },
        ) {
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
                        .background(backgroundColor)
                        .padding(tiny),
            )
        }
    }
}
