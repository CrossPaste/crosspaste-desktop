package com.clipevery.ui.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.clip.ClipContent.Companion.getClipItem
import com.clipevery.dao.clip.ClipData
import com.clipevery.dao.clip.ClipType
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.feed
import com.clipevery.ui.base.file
import com.clipevery.ui.base.html
import com.clipevery.ui.base.image
import com.clipevery.ui.base.link
import com.clipevery.ui.base.question
import com.valentinilk.shimmer.shimmer

@Composable
fun PrePreviewView(clipData: ClipData) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    Row {
        Column(
            modifier = Modifier.width(340.dp)
                .height(100.dp)
        ) {
            Row(
                modifier = Modifier
                    .width(340.dp)
                    .height(150.dp)
                    .background(MaterialTheme.colors.background)
                    .shimmer(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .padding(10.dp)
                        .background(Color.Gray)
                )

                Column(
                    modifier = Modifier.height(100.dp).width(199.dp).padding(10.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .width(199.dp)
                            .padding(bottom = 5.dp)
                            .background(Color.Gray)
                    )
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .width(160.dp)
                            .padding(vertical = 5.dp)
                            .background(Color.Gray)
                    )
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .width(199.dp).padding(top = 6.dp)
                            .background(Color.Gray)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.width(70.dp)
                .height(100.dp)
                .padding(start = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeInfo = getTypeInfo(clipData)

                Icon(
                    typeInfo.painter,
                    contentDescription = "Text",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.size(3.dp))
                Text(
                    text = copywriter.getText(typeInfo.text),
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

@Composable
fun getTypeInfo(clipData: ClipData): TypeInfo {
    return clipData.clipContent?.let {
        val clipType = it.clipAppearItems.mapNotNull { item -> getClipItem(item) }
            .maxByOrNull { clipItem -> clipItem.getClipType() }?.getClipType() ?: ClipType.INVALID
        when (clipType) {
            ClipType.TEXT -> {
                TypeInfo(feed(), "Text")
            }
            ClipType.URL -> {
                TypeInfo(link(), "Link")
            }
            ClipType.IMAGE -> {
                TypeInfo(image(), "Image")
            }
            ClipType.FILE -> {
                TypeInfo(file(), "File")
            }
            ClipType.HTML -> {
                TypeInfo(html(), "Html")
            }
            else -> {
                TypeInfo(question(), "Unknown")
            }
        }
    } ?: run {
        TypeInfo(question(), "Unknown")
    }
}

data class TypeInfo(val painter: Painter, val text: String)