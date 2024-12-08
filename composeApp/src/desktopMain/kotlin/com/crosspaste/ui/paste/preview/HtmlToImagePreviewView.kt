package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.ui.paste.GenerateImageView
import org.koin.compose.koinInject

@Composable
fun HtmlToImagePreviewView(pasteData: PasteData) {
    pasteData.getPasteItem(PasteHtml::class)?.let { pasteHtml ->
        val pasteMenuService = koinInject<DesktopPasteMenuService>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()

        val filePath by remember(pasteData.id) {
            mutableStateOf(
                pasteHtml.getHtmlImagePath(userDataPathProvider),
            )
        }

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(5.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            pasteMenuService.copyPasteData(pasteData)
                                        },
                                        onDoubleTap = {
                                            pasteMenuService.quickPaste(pasteData)
                                        },
                                    )
                                },
                    ) {
                        PasteContextMenuView(
                            items = pasteMenuService.pasteMenuItemsProvider(pasteData),
                        ) {
                            GenerateImageView(
                                modifier = Modifier.fillMaxSize(),
                                imagePath = filePath,
                                text = pasteHtml.getText(),
                                preview = true,
                                alignment = Alignment.TopStart,
                                contentScale = ContentScale.None,
                            )
                        }
                    }
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(
                    pasteData = pasteData,
                    toShow = toShow,
                )
            },
        )
    }
}
