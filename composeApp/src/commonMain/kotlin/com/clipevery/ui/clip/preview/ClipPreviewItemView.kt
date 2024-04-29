package com.clipevery.ui.clip.preview

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.ClipboardService
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipDao
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipState
import com.clipevery.dao.clip.ClipType
import com.clipevery.i18n.Copywriter
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.Toast
import com.clipevery.ui.base.ToastManager
import com.clipevery.ui.base.ToastStyle
import com.clipevery.ui.base.starRegular
import com.clipevery.ui.base.starSolid
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ClipPreviewItemView(
    clipData: ClipData,
    clipContent: @Composable ClipData.() -> Unit,
) {
    val current = LocalKoinApplication.current
    val clipDao = current.koin.get<ClipDao>()
    val clipboardService = current.koin.get<ClipboardService>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val toastManager = current.koin.get<ToastManager>()

    var hover by remember { mutableStateOf(false) }
    val backgroundColor = if (hover) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.background
    val scope = rememberCoroutineScope()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .onPointerEvent(
                    eventType = PointerEventType.Enter,
                    onEvent = {
                        hover = true
                    },
                )
                .onPointerEvent(
                    eventType = PointerEventType.Exit,
                    onEvent = {
                        hover = false
                    },
                )
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
                    .height(150.dp),
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(120.dp)
                        .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                clipData.clipContent()
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(color = MaterialTheme.colors.surface)
                        .height(30.dp)
                        .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier.width(350.dp)
                            .padding(start = 0.dp, end = 10.dp)
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        modifier =
                            Modifier.padding(0.dp)
                                .clickable {
                                    clipDao.setFavorite(clipData.id, !clipData.favorite)
                                },
                        painter = if (clipData.favorite) starSolid() else starRegular(),
                        contentDescription = "Favorite",
                        tint = if (clipData.favorite) Color(0xFFFFCE34) else MaterialTheme.colors.onSurface,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
                Row(
                    modifier =
                        Modifier.width(70.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color = MaterialTheme.colors.background)
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = getDateText(clipData.createTime, copywriter),
                        fontFamily = FontFamily.SansSerif,
                        style =
                            TextStyle(
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colors.onBackground,
                                fontSize = 10.sp,
                            ),
                    )
                }
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

@Composable
fun ClipSpecificPreviewContentView(
    clipAppearItem: ClipAppearItem,
    clipLeftContent: @Composable ClipAppearItem.() -> Unit,
    clipRightInfo: @Composable ClipAppearItem.() -> Unit,
) {
    Column(
        modifier =
            Modifier.width(340.dp)
                .height(100.dp),
    ) {
        clipAppearItem.clipLeftContent()
    }
    Column(
        modifier =
            Modifier.width(70.dp)
                .height(100.dp)
                .padding(start = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        clipAppearItem.clipRightInfo()
    }
}
