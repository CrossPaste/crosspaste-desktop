package com.clipevery.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun ClipPreviewItemView(clipData: ClipData, clipContent: @Composable ClipData.() -> Unit) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()

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
                    .padding(10.dp)
            ) {
                clipData.clipContent()
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(color = MaterialTheme.colors.surface)
                    .height(30.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.width(340.dp)
                    .padding(end = 10.dp)
                ) {
                    // todo label list ui
                }
                Row(modifier = Modifier.width(70.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color = MaterialTheme.colors.background)
                    .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
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
fun ClipSpecificPreviewView(clipData: ClipData) {
    if (clipData.preCreate) {
        PrePreviewView(clipData)
    } else {
        when(clipData.clipType) {
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
    clipRightInfo: @Composable ClipAppearItem.() -> Unit
) {
    Column(
        modifier = Modifier.width(340.dp)
            .height(100.dp)
    ) {
        clipAppearItem.clipLeftContent()
    }
    Column(
        modifier = Modifier.width(70.dp)
            .padding(start = 10.dp)
            .height(100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        clipAppearItem.clipRightInfo()
    }
}
