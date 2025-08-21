package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.paste.GenerateImageView
import com.crosspaste.ui.paste.PasteDataScope
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.RtfPreviewView() {
    getPasteItem(RtfPasteItem::class).let { rtfPasteItem ->
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
                ?: rtfPasteItem.getText()

        SimplePreviewContentView {
            GenerateImageView(
                modifier = Modifier.fillMaxSize(),
                imagePath = filePath,
                text = previewText,
                preview = true,
                alignment = Alignment.TopStart,
            )
        }
    }
}
