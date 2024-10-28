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
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.ui.paste.GenerateImageView
import org.koin.compose.koinInject

@Composable
fun RtfToImagePreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val pasteMenuService = koinInject<DesktopPasteMenuService>()
        val userDataPathProvider = koinInject<UserDataPathProvider>()

        val pasteRtf = it as PasteRtf

        val filePath by remember(pasteData.id) {
            mutableStateOf(
                pasteRtf.getRtfImagePath(userDataPathProvider),
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
                                        onDoubleTap = { onDoubleClick() },
                                    )
                                },
                    ) {
                        PasteContextMenuView(
                            items = pasteMenuService.pasteMenuItemsProvider(pasteData),
                        ) {
                            GenerateImageView(
                                modifier = Modifier.fillMaxSize(),
                                imagePath = filePath,
                                text = pasteRtf.getText(),
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
