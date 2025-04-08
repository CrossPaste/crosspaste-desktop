package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.db.paste.PasteData
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.paste.GenerateImageView
import org.koin.compose.koinInject

@Composable
fun RtfToImagePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteRtf::class)?.let { pasteRtf ->
        val userDataPathProvider = koinInject<UserDataPathProvider>()

        val filePath by remember(pasteData.id) {
            mutableStateOf(
                pasteRtf.getRtfImagePath(userDataPathProvider),
            )
        }

        SimplePreviewContentView(pasteData) {
            GenerateImageView(
                modifier = Modifier.fillMaxSize(),
                imagePath = filePath,
                text = pasteRtf.getText(),
                preview = true,
                alignment = Alignment.TopStart,
            )
        }
    }
}
