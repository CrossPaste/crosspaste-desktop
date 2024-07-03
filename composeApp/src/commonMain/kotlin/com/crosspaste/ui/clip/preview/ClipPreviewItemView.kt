package com.crosspaste.ui.clip.preview

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crosspaste.dao.clip.ClipCollection
import com.crosspaste.dao.clip.ClipData
import com.crosspaste.dao.clip.ClipItem
import com.crosspaste.dao.clip.ClipState
import com.crosspaste.dao.clip.ClipType
import com.crosspaste.i18n.Copywriter
import com.crosspaste.utils.getDateUtils
import io.realm.kotlin.types.RealmInstant
import kotlin.reflect.KClass
import kotlin.reflect.cast

fun <T : Any> ClipData.getClipItem(kclass: KClass<T>): T? {
    return ClipCollection.getClipItem(this.clipAppearItem)?.let {
        if (kclass.isInstance(it)) {
            kclass.cast(it)
        } else {
            null
        }
    }
}

fun ClipData.getClipItem(): ClipItem? {
    return ClipCollection.getClipItem(this.clipAppearItem)
}

@Composable
fun ClipPreviewItemView(
    clipData: ClipData,
    clipContent: @Composable ClipData.() -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(105.dp)
                .background(color = MaterialTheme.colors.surface),
    ) {
        Row(
            modifier =
                Modifier.fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 2.5.dp)
                    .clip(RoundedCornerShape(5.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            clipData.clipContent()
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
    backgroundColor: Color = MaterialTheme.colors.background,
    clipMainContent: @Composable () -> Unit,
    clipRightInfo: @Composable ((Boolean) -> Unit) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val width = animateDpAsState(targetValue = if (showMenu) 395.dp else 430.dp)

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
                clipMainContent()
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
                clipRightInfo { showMenu = it }
            }
        }
    }
}
