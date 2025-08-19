package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.paste.GenerateImageView
import com.crosspaste.ui.paste.PasteDataScope
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.RtfSidePreviewView() {
    getPasteItem(RtfPasteItem::class).let { rtfPasteItem ->
        val copywriter = koinInject<GlobalCopywriter>()
        val text = rtfPasteItem.getText()
        SidePasteLayoutView(
            pasteData = pasteData,
            pasteBottomContent = {
                BottomGradient(
                    text = copywriter.getText("character_count", "${text.length}"),
                )
            },
        ) {
            val userDataPathProvider = koinInject<UserDataPathProvider>()

            val filePath by remember(pasteData.id) {
                mutableStateOf(
                    rtfPasteItem.getRenderingFilePath(
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
