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
import com.crosspaste.paste.item.PasteItem
import com.crosspaste.paste.item.PasteRtf
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.paste.GenerateImageView
import com.crosspaste.utils.DateUtils.toLocalDateTime
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun RtfToImageDetailView(
    pasteData: PasteData,
    pasteRtf: PasteRtf,
    onDoubleClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val pasteItem = pasteRtf as PasteItem

    val fileUtils = getFileUtils()

    val filePath by remember(pasteData.id) {
        mutableStateOf(
            pasteRtf.getRtfImagePath(userDataPathProvider),
        )
    }

    PasteDetailView(
        detailView = {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()
            GenerateImageView(
                modifier =
                    Modifier.fillMaxSize()
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
                text = pasteRtf.getText(),
                preview = false,
                alignment = Alignment.TopStart,
            )
        },
        detailInfoView = {
            PasteDetailInfoView(
                pasteData = pasteData,
                items =
                    listOf(
                        PasteDetailInfoItem(TYPE, copywriter.getText("rtf")),
                        PasteDetailInfoItem(SIZE, fileUtils.formatBytes(pasteItem.size)),
                        PasteDetailInfoItem(REMOTE, copywriter.getText(if (pasteData.remote) "yes" else "no")),
                        PasteDetailInfoItem(
                            DATE,
                            copywriter.getDate(pasteData.createTime.toLocalDateTime()),
                        ),
                    ),
            )
        },
    )
}
