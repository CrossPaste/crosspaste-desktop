package com.clipevery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.CustomSwitch
import com.clipevery.ui.base.clock
import com.clipevery.ui.base.file
import com.clipevery.ui.base.hashtag
import com.clipevery.ui.base.image
import com.clipevery.ui.base.trash

@Composable
fun StoreSettingsView() {
    val current = LocalKoinApplication.current
    val configManager = current.koin.get<ConfigManager>()
    val clipDao = current.koin.get<ClipDao>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    var clipNumber: Long? by remember { mutableStateOf(null) }

    var clipImageSize: Long? by remember { mutableStateOf(null) }

    var clipFileSize: Long? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        val clipResourceInfo = clipDao.getClipResourceInfo()
        clipNumber = clipResourceInfo.clipNumber
        clipImageSize = clipResourceInfo.imageSize
        clipFileSize = clipResourceInfo.fileSize
    }

    Text( modifier = Modifier.wrapContentSize()
        .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("Store_Info"),
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.h6,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp)

    Column(modifier = Modifier.wrapContentSize()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colors.background)
    ) {
        Row(modifier = Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {

            Icon(
                modifier = Modifier.size(15.dp),
                painter = hashtag(),
                contentDescription = "number of pasteboards",
                tint = MaterialTheme.colors.onBackground
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Number_of_pasteboards"))

            Spacer(modifier = Modifier.width(10.dp))

            if (clipNumber != null) {
                settingsText("${clipNumber!!}")
            } else {
                CircularProgressIndicator(modifier = Modifier.size(25.dp))
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp), color = Color.Gray)

        Row(modifier = Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {

            Icon(
                modifier = Modifier.size(15.dp),
                painter = image(),
                contentDescription = "size of images",
                tint = MaterialTheme.colors.onBackground
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Size_of_images"))

            Spacer(modifier = Modifier.width(10.dp))

            if (clipImageSize != null) {
                settingsText("${clipImageSize!!}")
            } else {
                CircularProgressIndicator(modifier = Modifier.size(25.dp))
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp), color = Color.Gray)

        Row(modifier = Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {

            Icon(
                modifier = Modifier.size(15.dp),
                painter = file(),
                contentDescription = "size of files",
                tint = MaterialTheme.colors.onBackground
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Size_of_files"))

            Spacer(modifier = Modifier.width(10.dp))

            if (clipFileSize != null) {
                settingsText("${clipFileSize!!}")
            } else {
                CircularProgressIndicator(modifier = Modifier.size(25.dp))
            }
        }
    }

    Text( modifier = Modifier.wrapContentSize()
        .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("Clean_Up_Settings"),
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.h6,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp)

    Column(modifier = Modifier.wrapContentSize()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colors.background)
    ) {
        Row(modifier = Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {

            Icon(
                modifier = Modifier.size(15.dp),
                painter = trash(),
                contentDescription = "Image expiration time",
                tint = MaterialTheme.colors.onBackground
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Automatic_cleaning"))

            var isChecked by remember { mutableStateOf(false) }

            Spacer(modifier = Modifier.weight(1f))

            CustomSwitch(
                modifier = Modifier.width(32.dp)
                    .height(20.dp),
                checked = isChecked,
                onCheckedChange = { isChecked = it }
            )
        }

        Divider(modifier = Modifier.padding(start = 35.dp), color = Color.Gray)

        Row(modifier = Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = clock(),
                contentDescription = "Image expiration time",
                tint = MaterialTheme.colors.onBackground
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Image_expiration_time"))

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    modifier = Modifier.wrapContentSize(),
                    value = "1 week",
                    onValueChange = {},
                    readOnly = true,
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colors.onBackground
                    ))
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp), color = Color.Gray)

        Row(modifier = Modifier.fillMaxWidth()
            .height(40.dp)
            .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = clock(),
                contentDescription = "File expiration time",
                tint = MaterialTheme.colors.onBackground
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("File_expiration_time"))

            Spacer(modifier = Modifier.width(10.dp))
        }
    }



}

