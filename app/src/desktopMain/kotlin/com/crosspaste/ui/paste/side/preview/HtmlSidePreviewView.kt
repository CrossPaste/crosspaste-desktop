package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.HtmlPasteItem
import com.crosspaste.paste.item.PasteText
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.paste.GenerateImageView
import org.koin.compose.koinInject

@Composable
fun HtmlSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(HtmlPasteItem::class)?.let { htmlPasteItem ->
        val copywriter = koinInject<GlobalCopywriter>()
        val text = htmlPasteItem.getText()
        val backgroundColor = htmlPasteItem.getBackgroundColor()?.let { Color(it) } ?: Color.White
        SidePasteLayoutView(
            pasteData = pasteData,
            pasteBottomContent = {
                BottomGradient(
                    text = copywriter.getText("character_count", "${text.length}"),
                    backgroundColor = backgroundColor,
                )
            },
        ) {
            val userDataPathProvider = koinInject<UserDataPathProvider>()

            val filePath by remember(pasteData.id) {
                mutableStateOf(
                    htmlPasteItem.getRenderingFilePath(
                        pasteData.getPasteCoordinate(),
                        userDataPathProvider,
                    ),
                )
            }

            val previewText =
                pasteData.getPasteItem(PasteText::class)?.previewText()
                    ?: text

            GenerateImageView(
                modifier = Modifier.fillMaxSize(),
                imagePath = filePath,
                text = previewText,
                preview = false,
                alignment = Alignment.TopStart,
            )
        }
    }
}
