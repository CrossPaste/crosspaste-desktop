package com.crosspaste.ui.paste.side.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.paste.item.PasteText
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.paste.GenerateImageView
import org.koin.compose.koinInject

@Composable
fun HtmlSidePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteHtml::class)?.let { pasteHtml ->
        val text = pasteHtml.getText()
        SidePasteLayoutView(
            pasteData = pasteData,
            pasteBottomContent = {
                BottomGradient("${text.length}")
            },
        ) {
            val userDataPathProvider = koinInject<UserDataPathProvider>()

            val filePath by remember(pasteData.id) {
                mutableStateOf(
                    pasteHtml.getHtmlImagePath(userDataPathProvider),
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
