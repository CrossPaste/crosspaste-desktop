package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.PasteData
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteUrl
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.theme.AppUIFont.pasteUrlStyle
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun PasteUrlDetailView(
    pasteData: PasteData,
    pasteUrl: PasteUrl,
    onDoubleClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val fileUtils = getFileUtils()
    val url = pasteUrl.url
    val pasteItem = pasteUrl as PasteItem

    PasteDetailView(
        detailView = {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    uiSupport.openUrlInBrowser(pasteUrl.url)
                                },
                                onDoubleTap = {
                                    onDoubleClick()
                                },
                            )
                        }.padding(small3X),
            ) {
                Text(
                    text = url,
                    modifier = Modifier.fillMaxSize(),
                    overflow = TextOverflow.Ellipsis,
                    style = pasteUrlStyle,
                )
            }
        },
        detailInfoView = {
            PasteDetailInfoView(
                pasteData = pasteData,
                items =
                    listOf(
                        PasteDetailInfoItem(TYPE, copywriter.getText("link")),
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
