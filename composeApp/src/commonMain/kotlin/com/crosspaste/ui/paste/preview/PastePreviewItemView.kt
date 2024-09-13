package com.crosspaste.ui.paste.preview

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.app.AppWindowManager
import com.crosspaste.i18n.Copywriter
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.PasteboardService
import com.crosspaste.realm.paste.PasteData
import com.crosspaste.realm.paste.PasteState
import com.crosspaste.realm.paste.PasteType
import com.crosspaste.ui.base.MessageType
import com.crosspaste.ui.base.NotificationManager
import com.crosspaste.utils.getDateUtils
import com.crosspaste.utils.ioDispatcher
import com.crosspaste.utils.mainDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun PastePreviewItemView(
    pasteData: PasteData,
    pasteContent: @Composable PasteData.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(100.dp),
    ) {
        Row(
            modifier =
                Modifier.fillMaxSize()
                    .clip(RoundedCornerShape(5.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pasteData.pasteContent()
        }
    }
}

val dateUtils = getDateUtils()

@Composable
fun PasteSpecificPreviewView(pasteData: PasteData) {
    if (pasteData.pasteState == PasteState.LOADING) {
        PrePreviewView(pasteData)
    } else {
        val appWindowManager = koinInject<AppWindowManager>()
        val copywriter = koinInject<GlobalCopywriter>()
        val pasteboardService = koinInject<PasteboardService>()
        val notificationManager = koinInject<NotificationManager>()
        val scope = rememberCoroutineScope()
        val onDoubleClick: () -> Unit = {
            appWindowManager.setMainCursorWait()
            scope.launch(ioDispatcher) {
                pasteboardService.tryWritePasteboard(
                    pasteData,
                    localOnly = true,
                    filterFile = false,
                )
                withContext(mainDispatcher) {
                    appWindowManager.resetMainCursor()
                    notificationManager.addNotification(
                        message = copywriter.getText("copy_successful"),
                        messageType = MessageType.Success,
                    )
                }
            }
        }
        when (pasteData.pasteType) {
            PasteType.TEXT -> TextPreviewView(pasteData, onDoubleClick)
            PasteType.URL -> UrlPreviewView(pasteData, onDoubleClick)
            PasteType.HTML -> HtmlToImagePreviewView(pasteData, onDoubleClick)
            PasteType.IMAGE -> ImagesPreviewView(pasteData, onDoubleClick)
            PasteType.FILE -> FilesPreviewView(pasteData, onDoubleClick)
        }
    }
}

fun getDetailInfo(
    copywriter: Copywriter,
    pasteData: PasteData,
): String {
    val infos = mutableListOf<String>()
    pasteData.source?.let {
        infos.add(
            "${copywriter.getText("source")}: $it",
        )
    }
    val typeText =
        when (pasteData.pasteType) {
            PasteType.TEXT -> "text"
            PasteType.URL -> "link"
            PasteType.HTML -> "html"
            PasteType.IMAGE -> "image"
            PasteType.FILE -> "file"
            else -> "unknown"
        }
    infos.add(
        "${copywriter.getText("type")}: ${copywriter.getText(typeText)}",
    )
    pasteData.createTime.let {
        infos.add(
            "${copywriter.getText("create_time")}: ${copywriter.getDate(
                dateUtils.convertRealmInstantToLocalDateTime(it),
                true,
            )}",
        )
    }
    return infos.joinToString("\n")
}

@Composable
fun PasteSpecificPreviewContentView(
    backgroundColor: Color = MaterialTheme.colors.background,
    pasteMainContent: @Composable () -> Unit,
    pasteRightInfo: @Composable ((Boolean) -> Unit) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val width = animateDpAsState(targetValue = if (showMenu) (440 - 16 - 35).dp else (440 - 16).dp)

    Box(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(width.value)
                        .clip(RoundedCornerShape(5.dp))
                        .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                        .background(color = backgroundColor),
            ) {
                pasteMainContent()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.width(30.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                pasteRightInfo { showMenu = it }
            }
        }
    }
}
