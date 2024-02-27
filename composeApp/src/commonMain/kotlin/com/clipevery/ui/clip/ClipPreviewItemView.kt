package com.clipevery.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.clip.ClipAppearItem
import com.clipevery.dao.clip.ClipContent
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipType
import com.clipevery.i18n.Copywriter
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.utils.DateUtils
import io.realm.kotlin.types.RealmInstant
import kotlin.reflect.KClass
import kotlin.reflect.cast

fun <T: Any> ClipData.getClipItem(kclass: KClass<T>): T? {
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ClipPreviewItemView(clipData: ClipData, isLast: Boolean, clipContent: @Composable ClipAppearItem.() -> Unit) {

    clipData.getClipItem()?.let {
        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()

        if (it.getClipType() == ClipType.TEXT ||
            it.getClipType() == ClipType.URL ||
            it.getClipType() == ClipType.HTML ||
            it.getClipType() == ClipType.IMAGE ||
            it.getClipType() == ClipType.FILE
            ) {

            var hover by remember { mutableStateOf(false) }
            val backgroundColor = if (hover) MaterialTheme.colors.secondaryVariant else MaterialTheme.colors.background

            Row(modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .onPointerEvent(
                    eventType = PointerEventType.Enter,
                    onEvent = {
                        hover = true
                    }
                )
                .onPointerEvent(
                    eventType = PointerEventType.Exit,
                    onEvent = {
                        hover = false
                    }
                )
                .background(backgroundColor)) {

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .height(150.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .height(120.dp)
                    ) {
                        it.clipContent()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(color = MaterialTheme.colors.surface)
                            .height(30.dp)
                            .padding(end = 16.dp)
                        ,
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.wrapContentSize()
                            .clip(RoundedCornerShape(3.dp))
                            .background(color = MaterialTheme.colors.background)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = getDateText(clipData.createTime, copywriter),
                                fontFamily = FontFamily.SansSerif,
                                style = TextStyle(
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colors.onBackground,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }
            if (!isLast) {
                Divider(
                    color = MaterialTheme.colors.onBackground,
                    thickness = 2.dp
                )
            }
        }
    } ?: run {
        PrePreviewView(clipData)
        if (!isLast) {
            Divider(
                color = MaterialTheme.colors.onBackground,
                thickness = 2.dp
            )
        }
    }
}

fun getDateText(createTime: RealmInstant, copywriter: Copywriter): String {
    val date = DateUtils.convertRealmInstantToLocalDateTime(createTime)
    DateUtils.getDateText(date)?.let {
        return copywriter.getText(it)
    } ?: run {
        return copywriter.getDate(date)
    }
}

@Composable
fun ClipSpecificPreviewItemView(clipData: ClipData) {
    if (clipData.preCreate) {
        PrePreviewView(clipData)
    } else {
        when(clipData.clipType) {
            ClipType.TEXT -> TextPreviewView(clipData)
            ClipType.URL -> UrlPreviewView(clipData)
            ClipType.HTML -> HtmlToImagePreviewView(clipData)
            ClipType.IMAGE -> ImagePreviewView(clipData)
            ClipType.FILE -> FilePreviewView(clipData)
        }
    }
}
