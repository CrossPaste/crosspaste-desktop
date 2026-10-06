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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteItemReader
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.utils.ColorAccessibility
import com.crosspaste.utils.cpuDispatcher
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private class RichTextPreview(
    val charCount: Int,
    val richTextState: RichTextState,
)

/**
 * Side preview shared by HTML and RTF items.
 *
 * Both the character count and the preview markup need a full parse of the
 * stored document (Ksoup for HTML, Swing's RTFEditorKit for RTF); a 1 MB web
 * page takes a few hundred milliseconds. The parse and `setHtml` therefore
 * run on [cpuDispatcher]; the composition only shows the result.
 */
@OptIn(ExperimentalRichTextApi::class)
@Composable
fun PasteDataScope.RichTextSidePreviewView(
    pasteItem: PasteItem,
    backgroundColor: Int,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteItemReader = koinInject<PasteItemReader>()

    val themeBackground = MaterialTheme.colorScheme.background
    val itemBackground =
        Color(backgroundColor).let {
            if (it == Color.Transparent) themeBackground else it.compositeOver(themeBackground)
        }
    val isDark =
        remember(itemBackground) {
            ColorAccessibility.isDarkColor(itemBackground)
        }
    val richTextColor =
        if (isDark == LocalThemeState.current.isCurrentThemeDark) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.background
        }

    // Keyed on the id only, so an edit keeps showing the old preview until the new one is ready.
    var preview by remember(pasteData.id) { mutableStateOf<RichTextPreview?>(null) }
    val emptyState = remember { RichTextState() }

    LaunchedEffect(pasteData.id, pasteItem.hash) {
        preview =
            withContext(cpuDispatcher) {
                val richTextState = RichTextState()
                pasteItemReader.getPreviewHtml(pasteItem)?.let { richTextState.setHtml(it) }
                RichTextPreview(
                    charCount = pasteItemReader.getText(pasteItem).length,
                    richTextState = richTextState,
                )
            }
    }

    SidePasteLayoutView(
        pasteBottomContent = {
            BottomGradient(
                text = preview?.let { copywriter.getText("character_count", "${it.charCount}") } ?: "",
                backgroundColor = itemBackground,
            )
        },
    ) {
        RichText(
            color = richTextColor,
            state = preview?.richTextState ?: emptyState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(itemBackground)
                    .padding(small2X),
        )
    }
}
