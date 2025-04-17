package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteText
import com.crosspaste.ui.paste.PasteboardViewProvider.Companion.previewTextStyle
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun PasteTextDetailView(
    pasteData: PasteData,
    pasteText: PasteText,
    onDoubleClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val fileUtils = getFileUtils()
    val text = pasteText.text
    val pasteItem = pasteText as PasteItem

    PasteDetailView(
        detailView = {
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
                Text(
                    text = text,
                    modifier =
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    overflow = TextOverflow.Ellipsis,
                    style = previewTextStyle,
                )
            }
        },
        detailInfoView = {
            PasteDetailInfoView(
                pasteData = pasteData,
                items =
                    listOf(
                        PasteDetailInfoItem(TYPE, copywriter.getText("text")),
                        PasteDetailInfoItem(SIZE, fileUtils.formatBytes(pasteItem.size)),
                        PasteDetailInfoItem(REMOTE, copywriter.getText(if (pasteData.remote) "yes" else "no")),
                        PasteDetailInfoItem(
                            DATE,
                            copywriter.getDate(
                                DateUtils.epochMillisecondsToLocalDateTime(pasteData.createTime),
                            ),
                        ),
                    ),
            )
        },
    )
}
