package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.COLOR
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.ColorPasteItem
import com.crosspaste.ui.paste.PasteDataScope
import com.crosspaste.ui.theme.AppUISize.colossal
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun PasteDataScope.ColorDetailView(onDoubleClick: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    val fileUtils = getFileUtils()

    val colorPasteItem = getPasteItem(ColorPasteItem::class)

    PasteDetailView(
        detailView = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onDoubleClick()
                                },
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(gigantic)
                            .clip(tiny2XRoundedCornerShape)
                            .background(Color(colorPasteItem.color).copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(colossal)
                                .shadow(
                                    elevation = tiny5X,
                                    shape = tiny2XRoundedCornerShape,
                                    spotColor = Color.Black.copy(alpha = 0.1f),
                                ).clip(tiny2XRoundedCornerShape)
                                .background(Color(colorPasteItem.color)),
                    )
                }
            }
        },
        detailInfoView = {
            PasteDetailInfoView(
                items =
                    listOf(
                        PasteDetailInfoItem(
                            key = COLOR,
                            value = colorPasteItem.toHexString(),
                        ),
                        PasteDetailInfoItem(TYPE, copywriter.getText("color")),
                        PasteDetailInfoItem(SIZE, fileUtils.formatBytes(4L)),
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
