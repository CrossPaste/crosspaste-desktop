package com.clipevery.ui.clip.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipboardService
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipState
import com.clipevery.dao.clip.ClipType
import com.clipevery.i18n.Copywriter
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.Toast
import com.clipevery.ui.base.ToastManager
import com.clipevery.ui.base.ToastStyle
import com.clipevery.utils.getDateUtils
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.reflect.cast

fun <T : Any> ClipData.getClipItem(kclass: KClass<T>): T? {
    return ClipContent.getClipItem(this.clipAppearContent)?.let {
        if (kclass.isInstance(it)) {
            kclass.cast(it)
        } else {
            null
        }
    }
}

fun ClipData.getClipItem(): ClipAppearItem? {
    return ClipContent.getClipItem(this.clipAppearContent)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipPreviewItemView(
    clipData: ClipData,
    clipContent: @Composable ClipData.() -> Unit,
) {
    val current = LocalKoinApplication.current
    val clipboardService = current.koin.get<ClipboardService>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val toastManager = current.koin.get<ToastManager>()

    val backgroundColor = MaterialTheme.colors.background
    val scope = rememberCoroutineScope()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .onClick(
                    onDoubleClick = {
                        if (clipData.clipState == ClipState.LOADED) {
                            scope.launch {
                                clipboardService.tryWriteClipboard(clipData, localOnly = true, filterFile = false)
                                toastManager.setToast(
                                    Toast(
                                        ToastStyle.success,
                                        copywriter.getText("Copy_Successful"),
                                        3000,
                                    ),
                                )
                            }
                        }
                    },
                    onClick = {},
                )
                .background(backgroundColor),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .height(120.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(120.dp)
                        .padding(10.dp, 10.dp, 0.dp, 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                clipData.clipContent()
            }
        }
    }
}

val dateUtils = getDateUtils()

fun getDateText(
    createTime: RealmInstant,
    copywriter: Copywriter,
): String {
    val date = dateUtils.convertRealmInstantToLocalDateTime(createTime)
    dateUtils.getDateText(date)?.let {
        return copywriter.getText(it)
    } ?: run {
        return copywriter.getDate(date)
    }
}

@Composable
fun ClipSpecificPreviewView(clipData: ClipData) {
    if (clipData.clipState == ClipState.LOADING) {
        PrePreviewView(clipData)
    } else {
        when (clipData.clipType) {
            ClipType.TEXT -> TextPreviewView(clipData)
            ClipType.URL -> UrlPreviewView(clipData)
            ClipType.HTML -> HtmlToImagePreviewView(clipData)
            ClipType.IMAGE -> ImagesPreviewView(clipData)
            ClipType.FILE -> FilesPreviewView(clipData)
        }
    }
}

fun getTypeText(clipType: Int): String {
    return when (clipType) {
        ClipType.TEXT -> "Text"
        ClipType.URL -> "Link"
        ClipType.HTML -> "Html"
        ClipType.IMAGE -> "Image"
        ClipType.FILE -> "File"
        else -> "Unknown"
    }
}

@Composable
fun ClipSpecificPreviewContentView(
    clipLeftContent: @Composable () -> Unit,
    clipRightInfo: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxHeight().width(400.dp)
                    .clip(RoundedCornerShape(5.dp)),
        ) {
            clipLeftContent()
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
        ) {
            clipRightInfo()
        }
    }
}
