package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.PasteText
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.paste.GenerateImageView
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.RtfToImageDetailView(onDoubleClick: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()

    val fileUtils = getFileUtils()
    val rtfPasteItem = getPasteItem(RtfPasteItem::class)

    val filePath by remember(pasteData.id) {
        mutableStateOf(
            rtfPasteItem.getRenderingFilePath(
                pasteData.getPasteCoordinate(),
                userDataPathProvider,
            ),
        )
    }

    PasteDetailView(
        detailView = {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()
            GenerateImageView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                        .verticalScroll(verticalScrollState)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    uiSupport.openRtf(pasteData)
                                },
                                onDoubleTap = {
                                    onDoubleClick()
                                },
                            )
                        },
                imagePath = filePath,
                text = pasteData.getPasteItem(PasteText::class)?.text ?: rtfPasteItem.getText(),
                preview = false,
                alignment = Alignment.TopStart,
            )
        },
        detailInfoView = {
            PasteDetailInfoView(
                items =
                    listOf(
                        PasteDetailInfoItem(TYPE, copywriter.getText("rtf")),
                        PasteDetailInfoItem(SIZE, fileUtils.formatBytes(rtfPasteItem.size)),
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
