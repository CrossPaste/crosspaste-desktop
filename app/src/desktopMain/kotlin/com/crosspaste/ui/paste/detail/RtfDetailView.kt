package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.RtfPasteItem
import com.crosspaste.ui.LocalThemeState
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getColorUtils
import com.crosspaste.utils.getFileUtils
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.RtfDetailView(onDoubleClick: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    val uiSupport = koinInject<UISupport>()
    val rtfPasteItem = getPasteItem(RtfPasteItem::class)

    val colorUtils = getColorUtils()
    val fileUtils = getFileUtils()

    val backgroundColor by remember(pasteData.id) {
        mutableStateOf(Color(rtfPasteItem.getBackgroundColor()))
    }

    val rtfBackground =
        if (backgroundColor == Color.Transparent) {
            MaterialTheme.colorScheme.background
        } else {
            backgroundColor
        }
    val isDark by remember(pasteData.id) { mutableStateOf(colorUtils.isDarkColor(rtfBackground)) }
    val richTextColor =
        if (isDark == LocalThemeState.current.isCurrentThemeDark) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.background
        }

    PasteDetailView(
        detailView = {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()
            val state = rememberRichTextState()

            LaunchedEffect(rtfPasteItem.getHtml()) {
                state.setHtml(rtfPasteItem.getHtml())
            }

            RichText(
                color = richTextColor,
                state = state,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                        .verticalScroll(verticalScrollState)
                        .background(rtfBackground)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    uiSupport.openRtf(pasteData)
                                },
                                onDoubleTap = {
                                    onDoubleClick()
                                },
                            )
                        }.padding(tiny),
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
