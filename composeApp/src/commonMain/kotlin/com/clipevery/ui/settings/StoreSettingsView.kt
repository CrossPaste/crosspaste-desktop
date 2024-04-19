package com.clipevery.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.clipevery.LocalKoinApplication
import com.clipevery.clean.CleanTime
import com.clipevery.config.ConfigManager
import com.clipevery.dao.clip.ClipDao
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.ui.base.CustomSwitch
import com.clipevery.ui.base.anglesUpDown
import com.clipevery.ui.base.clock
import com.clipevery.ui.base.feed
import com.clipevery.ui.base.file
import com.clipevery.ui.base.hashtag
import com.clipevery.ui.base.html
import com.clipevery.ui.base.image
import com.clipevery.ui.base.link
import com.clipevery.ui.base.trash
import com.clipevery.ui.devices.measureTextWidth
import com.clipevery.utils.FileUtils
import com.clipevery.utils.Quadruple

@Composable
fun StoreSettingsView() {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val configManager = current.koin.get<ConfigManager>()
    val clipDao = current.koin.get<ClipDao>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val fileUtils = current.koin.get<FileUtils>()

    var clipCount: Long? by remember { mutableStateOf(null) }
    var clipFormatSize: String? by remember { mutableStateOf(null) }

    var textCount: Long? by remember { mutableStateOf(null) }
    var textFormatSize: String? by remember { mutableStateOf(null) }

    var urlCount: Long? by remember { mutableStateOf(null) }
    var urlFormatSize: String? by remember { mutableStateOf(null) }

    var htmlCount: Long? by remember { mutableStateOf(null) }
    var htmlFormatSize: String? by remember { mutableStateOf(null) }

    var imageCount: Long? by remember { mutableStateOf(null) }
    var imageFormatSize: String? by remember { mutableStateOf(null) }

    var fileCount: Long? by remember { mutableStateOf(null) }
    var fileFormatSize: String? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) {
        val clipResourceInfo = clipDao.getClipResourceInfo()
        clipCount = clipResourceInfo.clipCount
        clipFormatSize = fileUtils.formatBytes(clipResourceInfo.clipSize)

        textCount = clipResourceInfo.textCount
        textFormatSize = fileUtils.formatBytes(clipResourceInfo.textSize)

        urlCount = clipResourceInfo.urlCount
        urlFormatSize = fileUtils.formatBytes(clipResourceInfo.urlSize)

        htmlCount = clipResourceInfo.htmlCount
        htmlFormatSize = fileUtils.formatBytes(clipResourceInfo.htmlSize)

        imageCount = clipResourceInfo.imageCount
        imageFormatSize = fileUtils.formatBytes(clipResourceInfo.imageSize)

        fileCount = clipResourceInfo.fileCount
        fileFormatSize = fileUtils.formatBytes(clipResourceInfo.fileSize)
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("Store_Info"),
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.h6,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    )

    var nameMaxWidth by remember { mutableStateOf(0.dp) }

    val clipTypes: Array<Quadruple<String, Painter, Long?, String?>> =
        arrayOf(
            Quadruple("Pasteboard", hashtag(), clipCount, clipFormatSize),
            Quadruple("Text", feed(), textCount, textFormatSize),
            Quadruple("Link", link(), urlCount, urlFormatSize),
            Quadruple("Html", html(), htmlCount, htmlFormatSize),
            Quadruple("Image", image(), imageCount, imageFormatSize),
            Quadruple("File", file(), fileCount, fileFormatSize),
        )

    val textStyle =
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.SansSerif,
        )

    for (property in clipTypes) {
        nameMaxWidth = maxOf(nameMaxWidth, measureTextWidth(copywriter.getText(property.first), textStyle))
    }

    Column(
        modifier =
            Modifier.wrapContentSize()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colors.background),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(nameMaxWidth + 23.dp))

            Row(
                modifier = Modifier.weight(0.25f),
                horizontalArrangement = Arrangement.End,
            ) {
                settingsText(copywriter.getText("Count"))
            }

            Row(
                modifier = Modifier.weight(0.3f),
                horizontalArrangement = Arrangement.End,
            ) {
                settingsText(copywriter.getText("Size"))
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp))

        clipTypes.forEachIndexed { index, quadruple ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(15.dp),
                    painter = quadruple.second,
                    contentDescription = "pasteboard",
                    tint = MaterialTheme.colors.onBackground,
                )
                Spacer(modifier = Modifier.width(8.dp))

                settingsText(
                    copywriter.getText(quadruple.first),
                    modifier = Modifier.width(nameMaxWidth),
                )

                Row(
                    modifier = Modifier.weight(0.25f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (quadruple.third != null) {
                        settingsText("${quadruple.third}")
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(25.dp))
                    }
                }

                Row(
                    modifier = Modifier.weight(0.3f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (quadruple.fourth != null) {
                        settingsText(quadruple.fourth)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(25.dp))
                    }
                }
            }

            if (index != clipTypes.size - 1) {
                Divider(modifier = Modifier.padding(start = 35.dp))
            }
        }
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("Clean_Up_Settings"),
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.h6,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    )

    Column(
        modifier =
            Modifier.wrapContentSize()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colors.background),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = trash(),
                contentDescription = "Image expiration time",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Automatic_cleaning"))

            var isAutoCleaning by remember { mutableStateOf(configManager.config.isAutoCleaning) }

            Spacer(modifier = Modifier.weight(1f))

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = isAutoCleaning,
                onCheckedChange = { newIsAutoCleaning ->
                    configManager.updateConfig { it.copy(isAutoCleaning = newIsAutoCleaning) }
                    isAutoCleaning = configManager.config.isAutoCleaning
                },
            )
        }

        Divider(modifier = Modifier.padding(start = 35.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = clock(),
                contentDescription = "Image expiration time",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("Image_expiration_time"))

            Spacer(modifier = Modifier.weight(1f))

            var selectImageCleanTimeIndex by remember { mutableStateOf(configManager.config.imageCleanTimeIndex) }

            val imageCleanTime = CleanTime.entries[selectImageCleanTimeIndex]

            var imageCleanTimeValue by remember(copywriter.language()) {
                mutableStateOf("${imageCleanTime.quantity} ${copywriter.getText(imageCleanTime.unit)}")
            }
            val imageCleanTimeWidth = measureTextWidth(imageCleanTimeValue, SettingsTextStyle())

            var showImageCleanTimeMenu by remember { mutableStateOf(false) }

            Row(
                modifier =
                    Modifier.wrapContentWidth()
                        .clickable {
                            showImageCleanTimeMenu = !showImageCleanTimeMenu
                        },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.width(imageCleanTimeWidth),
                    text = imageCleanTimeValue,
                    style = SettingsTextStyle(),
                )

                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    modifier = Modifier.size(15.dp),
                    painter = anglesUpDown(),
                    contentDescription = "File expiration time",
                    tint = MaterialTheme.colors.onBackground,
                )
            }

            if (showImageCleanTimeMenu) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    offset =
                        IntOffset(
                            with(density) { (-(imageCleanTimeWidth + 30.dp)).roundToPx() },
                            with(density) { (0.dp).roundToPx() },
                        ),
                    onDismissRequest = {
                        if (showImageCleanTimeMenu) {
                            showImageCleanTimeMenu = false
                        }
                    },
                    properties =
                        PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                        ),
                ) {
                    CleanTimeMenuView(selectImageCleanTimeIndex) { index ->
                        configManager.updateConfig { it.copy(imageCleanTimeIndex = index) }
                        selectImageCleanTimeIndex = configManager.config.imageCleanTimeIndex
                        val currentImageCleanTime = CleanTime.entries[selectImageCleanTimeIndex]
                        imageCleanTimeValue = "${currentImageCleanTime.quantity} ${copywriter.getText(currentImageCleanTime.unit)}"
                        showImageCleanTimeMenu = false
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = clock(),
                contentDescription = "File expiration time",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("File_expiration_time"))

            Spacer(modifier = Modifier.weight(1f))

            var selectFileCleanTimeIndex by remember { mutableStateOf(configManager.config.fileCleanTimeIndex) }

            val fileCleanTime = CleanTime.entries[selectFileCleanTimeIndex]

            var fileCleanTimeValue by remember(copywriter.language()) {
                mutableStateOf("${fileCleanTime.quantity} ${copywriter.getText(fileCleanTime.unit)}")
            }
            val fileCleanTimeWidth = measureTextWidth(fileCleanTimeValue, SettingsTextStyle())

            var showFileCleanTimeMenu by remember { mutableStateOf(false) }

            Row(
                modifier =
                    Modifier.wrapContentWidth()
                        .clickable {
                            showFileCleanTimeMenu = !showFileCleanTimeMenu
                        },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.width(fileCleanTimeWidth),
                    text = fileCleanTimeValue,
                    style = SettingsTextStyle(),
                )

                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    modifier = Modifier.size(15.dp),
                    painter = anglesUpDown(),
                    contentDescription = "File expiration time",
                    tint = MaterialTheme.colors.onBackground,
                )
            }

            if (showFileCleanTimeMenu) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    offset =
                        IntOffset(
                            with(density) { (-(fileCleanTimeWidth + 30.dp)).roundToPx() },
                            with(density) { (0.dp).roundToPx() },
                        ),
                    onDismissRequest = {
                        if (showFileCleanTimeMenu) {
                            showFileCleanTimeMenu = false
                        }
                    },
                    properties =
                        PopupProperties(
                            focusable = true,
                            dismissOnBackPress = true,
                            dismissOnClickOutside = true,
                        ),
                ) {
                    CleanTimeMenuView(selectFileCleanTimeIndex) { index ->
                        configManager.updateConfig { it.copy(fileCleanTimeIndex = index) }
                        selectFileCleanTimeIndex = configManager.config.fileCleanTimeIndex
                        val currentFileCleanTime = CleanTime.entries[selectFileCleanTimeIndex]
                        fileCleanTimeValue = "${currentFileCleanTime.quantity} ${copywriter.getText(currentFileCleanTime.unit)}"
                        showFileCleanTimeMenu = false
                    }
                }
            }
        }
    }
}
