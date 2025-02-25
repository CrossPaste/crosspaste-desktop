package com.crosspaste.ui.paste.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.crosspaste.db.paste.PasteData
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.info.PasteInfos.COLOR
import com.crosspaste.info.PasteInfos.DATE
import com.crosspaste.info.PasteInfos.REMOTE
import com.crosspaste.info.PasteInfos.SIZE
import com.crosspaste.info.PasteInfos.TYPE
import com.crosspaste.paste.item.PasteColor
import com.crosspaste.utils.DateUtils
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun PasteColorDetailView(
    pasteData: PasteData,
    pasteColor: PasteColor,
    onDoubleClick: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val fileUtils = getFileUtils()

    PasteDetailView(
        detailView = {
            Box(
                modifier =
                    Modifier.fillMaxSize()
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
                            .size(150.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(pasteColor.color).copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(120.dp)
                                .shadow(
                                    elevation = 1.dp,
                                    shape = RoundedCornerShape(5.dp),
                                    spotColor = Color.Black.copy(alpha = 0.1f),
                                )
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(pasteColor.color)),
                    )
                }
            }
        },
        detailInfoView = {
            PasteDetailInfoView(
                pasteData = pasteData,
                items =
                    listOf(
                        PasteDetailInfoItem(
                            key = COLOR,
                            value = pasteColor.toHexString(),
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
