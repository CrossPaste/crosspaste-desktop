package com.crosspaste.ui.paste.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.paste.DesktopPasteMenuService
import com.crosspaste.paste.item.PasteText
import com.crosspaste.realm.paste.PasteData
import org.koin.compose.koinInject

@Composable
fun TextPreviewView(
    pasteData: PasteData,
    onDoubleClick: () -> Unit,
) {
    pasteData.getPasteItem()?.let {
        val pasteMenuService = koinInject<DesktopPasteMenuService>()

        PasteSpecificPreviewContentView(
            pasteMainContent = {
                Row(
                    modifier =
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onDoubleClick()
                                    },
                                )
                            }
                            .padding(10.dp),
                ) {
                    PasteContextMenuView(
                        items = pasteMenuService.pasteMenuItemsProvider(pasteData),
                    ) {
                        Text(
                            modifier = Modifier.fillMaxSize(),
                            text = (it as PasteText).text,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 4,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                TextStyle(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontSize = 14.sp,
                                ),
                        )
                    }
                }
            },
            pasteRightInfo = { toShow ->
                PasteMenuView(pasteData = pasteData, toShow = toShow)
            },
        )
    }
}
