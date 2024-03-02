package com.clipevery.ui.clip

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.clip.item.ClipFiles
import com.clipevery.dao.clip.ClipData
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.image

@Composable
fun ImagePreviewView(clipData: ClipData) {
    clipData.getClipItem()?.let {

        val current = LocalKoinApplication.current
        val copywriter = current.koin.get<GlobalCopywriter>()
        val clipFiles = it as ClipFiles

        ClipSpecificPreviewContentView(it, {
            val imagePaths = clipFiles.getFilePaths()
            LazyRow(modifier = Modifier.fillMaxSize()) {
                items(imagePaths.size) { index ->
                    SingleImagePreviewView(imagePaths[index])
                    if (index != imagePaths.size - 1) {
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                }
            }
        }, {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    image(),
                    contentDescription = "Image",
                    modifier = Modifier.padding(3.dp).size(14.dp),
                    tint = MaterialTheme.colors.onBackground
                )
                Spacer(modifier = Modifier.size(3.dp))
                Text(
                    text = copywriter.getText("Image"),
                    fontFamily = FontFamily.SansSerif,
                    style = TextStyle(
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground,
                        fontSize = 10.sp
                    )
                )
            }
        })
    }
}
