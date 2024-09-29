package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.onClick
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.PasteHtml
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteItem
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.paste.preview.HtmlToImageView
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HtmlToImageDetailView(
    pasteData: PasteData,
    pasteHtml: PasteHtml,
    onDoubleClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val userDataPathProvider = koinInject<UserDataPathProvider>()
    val pasteItem = pasteHtml as PasteItem

    val dateUtils = getDateUtils()
    val fileUtils = getFileUtils()

    val filePath by remember(pasteData.id) {
        mutableStateOf(
            pasteHtml.getHtmlImagePath(userDataPathProvider),
        )
    }

    PasteDetailView(
        detailView = {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()
            HtmlToImageView(
                modifier =
                    Modifier.fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                        .verticalScroll(verticalScrollState)
                        .onClick(
                            onDoubleClick = onDoubleClick,
                            onClick = {
                                uiSupport.openHtml(pasteData.id, pasteHtml.html)
                            },
                        ),
                html2ImagePath = filePath,
                htmlText = pasteHtml.getText(),
                preview = false,
            )
        },
        detailInfoView = {
            PasteDetailInfoView(
                pasteData = pasteData,
                items =
                    listOf(
                        PasteDetailInfoItem(TYPE, copywriter.getText("html")),
                        PasteDetailInfoItem(SIZE, fileUtils.formatBytes(pasteItem.size)),
                        PasteDetailInfoItem(REMOTE, copywriter.getText(if (pasteData.remote) "yes" else "no")),
                        PasteDetailInfoItem(
                            DATE,
                            copywriter.getDate(
                                dateUtils.convertRealmInstantToLocalDateTime(pasteData.createTime),
                                true,
                            ),
                        ),
                    ),
            )
        },
    )
}
