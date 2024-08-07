package com.crosspaste.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.LocalKoinApplication
import com.crosspaste.clean.CleanTime
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.path.UserDataPathProvider
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.CustomRectangleSwitch
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.CustomTextField
import com.crosspaste.ui.base.anglesUpDown
import com.crosspaste.ui.base.archive
import com.crosspaste.ui.base.clock
import com.crosspaste.ui.base.database
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.hashtag
import com.crosspaste.ui.base.html
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.percent
import com.crosspaste.ui.base.text
import com.crosspaste.ui.base.trash
import com.crosspaste.ui.connectedColor
import com.crosspaste.ui.devices.measureTextWidth
import com.crosspaste.ui.disconnectedColor
import com.crosspaste.utils.Quadruple
import com.crosspaste.utils.getFileUtils

@Composable
fun StoreSettingsView() {
    val current = LocalKoinApplication.current
    val density = LocalDensity.current
    val configManager = current.koin.get<ConfigManager>()
    val pasteDao = current.koin.get<PasteDao>()
    val copywriter = current.koin.get<GlobalCopywriter>()
    val userDataPathProvider = current.koin.get<UserDataPathProvider>()
    val fileUtils = getFileUtils()

    var pasteCount: Long? by remember { mutableStateOf(null) }
    var pasteFormatSize: String? by remember { mutableStateOf(null) }

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

    var allOrFavorite by remember { mutableStateOf(true) }

    val refresh: (Boolean) -> Unit = {
        val pasteResourceInfo = pasteDao.getPasteResourceInfo(it)
        pasteCount = pasteResourceInfo.pasteCount
        pasteFormatSize = fileUtils.formatBytes(pasteResourceInfo.pasteSize)

        textCount = pasteResourceInfo.textCount
        textFormatSize = fileUtils.formatBytes(pasteResourceInfo.textSize)

        urlCount = pasteResourceInfo.urlCount
        urlFormatSize = fileUtils.formatBytes(pasteResourceInfo.urlSize)

        htmlCount = pasteResourceInfo.htmlCount
        htmlFormatSize = fileUtils.formatBytes(pasteResourceInfo.htmlSize)

        imageCount = pasteResourceInfo.imageCount
        imageFormatSize = fileUtils.formatBytes(pasteResourceInfo.imageSize)

        fileCount = pasteResourceInfo.fileCount
        fileFormatSize = fileUtils.formatBytes(pasteResourceInfo.fileSize)
    }

    LaunchedEffect(Unit) {
        refresh(allOrFavorite)
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("store_info"),
        color = MaterialTheme.colors.onBackground,
        style = MaterialTheme.typography.h6,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    )

    var nameMaxWidth by remember { mutableStateOf(96.dp) }

    val pasteTypes: Array<Quadruple<String, Painter, Long?, String?>> =
        arrayOf(
            Quadruple("pasteboard", hashtag(), pasteCount, pasteFormatSize),
            Quadruple("text", text(), textCount, textFormatSize),
            Quadruple("link", link(), urlCount, urlFormatSize),
            Quadruple("html", html(), htmlCount, htmlFormatSize),
            Quadruple("image", image(), imageCount, imageFormatSize),
            Quadruple("file", file(), fileCount, fileFormatSize),
        )

    val textStyle =
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.SansSerif,
        )

    for (property in pasteTypes) {
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
            Row(
                modifier = Modifier.wrapContentSize(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomRectangleSwitch(
                    modifier = Modifier.width(96.dp).height(30.dp),
                    checked = allOrFavorite,
                    onCheckedChange = { newAllOrFavorite ->
                        allOrFavorite = newAllOrFavorite
                        refresh(allOrFavorite)
                    },
                    textStyle =
                        TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.SansSerif,
                        ),
                    checkedText = copywriter.getText("all_storage"),
                    uncheckedText = copywriter.getText("favorite_storage"),
                )
            }

            Row(
                modifier = Modifier.weight(0.25f),
                horizontalArrangement = Arrangement.End,
            ) {
                settingsText(copywriter.getText("count"))
            }

            Row(
                modifier = Modifier.weight(0.3f),
                horizontalArrangement = Arrangement.End,
            ) {
                settingsText(copywriter.getText("size"))
            }
        }

        Divider(modifier = Modifier.padding(start = 35.dp))

        pasteTypes.forEachIndexed { index, quadruple ->
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
                    modifier = Modifier.weight(0.2f),
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

            if (index != pasteTypes.size - 1) {
                Divider(modifier = Modifier.padding(start = 35.dp))
            }
        }
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("storage_path"),
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
        var useDefaultStoragePath by remember { mutableStateOf(configManager.config.useDefaultStoragePath) }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = archive(),
                contentDescription = "user default storage path",
                tint = Color(0xFF41B06E),
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("use_default_storage_path"))

            Spacer(modifier = Modifier.weight(1f))

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = useDefaultStoragePath,
                onCheckedChange = {
                    useDefaultStoragePath = !useDefaultStoragePath
                },
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomTextField(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                value = userDataPathProvider.getUserDataPath().toString(),
                onValueChange = {},
                enabled = !useDefaultStoragePath,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle =
                    LocalTextStyle.current.copy(
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 10.sp,
                    ),
                colors =
                    TextFieldDefaults.textFieldColors(
                        focusedIndicatorColor = MaterialTheme.colors.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                contentPadding = PaddingValues(0.dp),
            )
        }

        if (!useDefaultStoragePath) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    modifier = Modifier.height(28.dp),
                    onClick = {
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, disconnectedColor()),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
                    elevation =
                        ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            focusedElevation = 0.dp,
                        ),
                ) {
                    Text(
                        text = copywriter.getText("cancel"),
                        color = disconnectedColor(),
                        style =
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                            ),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    modifier = Modifier.height(28.dp),
                    onClick = {
                    },
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, connectedColor()),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
                    elevation =
                        ButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            hoveredElevation = 0.dp,
                            focusedElevation = 0.dp,
                        ),
                ) {
                    Text(
                        text = copywriter.getText("confirm"),
                        color = connectedColor(),
                        style =
                            TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Light,
                                fontSize = 14.sp,
                            ),
                    )
                }
            }
        }
    }

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("auto_cleanup_settings"),
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
                contentDescription = "trash",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("expiration_cleanup"))

            var isExpirationCleanup by remember { mutableStateOf(configManager.config.isExpirationCleanup) }

            Spacer(modifier = Modifier.weight(1f))

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = isExpirationCleanup,
                onCheckedChange = { newIsExpirationCleanup ->
                    configManager.updateConfig("isExpirationCleanup", newIsExpirationCleanup)
                    isExpirationCleanup = configManager.config.isExpirationCleanup
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
                contentDescription = "Image Expiry Period",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("image_expiry_period"))

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
                    contentDescription = "Image expiration time",
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
                        configManager.updateConfig("imageCleanTimeIndex", index)
                        selectImageCleanTimeIndex = configManager.config.imageCleanTimeIndex
                        val currentImageCleanTime = CleanTime.entries[selectImageCleanTimeIndex]
                        imageCleanTimeValue =
                            "${currentImageCleanTime.quantity} ${copywriter.getText(currentImageCleanTime.unit)}"
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
                contentDescription = "File Expiry Period",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("file_expiry_period"))

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
                    contentDescription = "File Expiry Period",
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
                        configManager.updateConfig("fileCleanTimeIndex", index)
                        selectFileCleanTimeIndex = configManager.config.fileCleanTimeIndex
                        val currentFileCleanTime = CleanTime.entries[selectFileCleanTimeIndex]
                        fileCleanTimeValue =
                            "${currentFileCleanTime.quantity} ${copywriter.getText(currentFileCleanTime.unit)}"
                        showFileCleanTimeMenu = false
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

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
                contentDescription = "trash",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("threshold_cleanup"))

            var isThresholdCleanup by remember { mutableStateOf(configManager.config.isThresholdCleanup) }

            Spacer(modifier = Modifier.weight(1f))

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = isThresholdCleanup,
                onCheckedChange = { newIsThresholdCleanup ->
                    configManager.updateConfig("isThresholdCleanup", newIsThresholdCleanup)
                    isThresholdCleanup = configManager.config.isThresholdCleanup
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
                painter = database(),
                contentDescription = "Maximum Storage",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("maximum_storage"))

            Spacer(modifier = Modifier.weight(1f))

            val maxStorage by remember { mutableStateOf(configManager.config.maxStorage) }

            Row(
                modifier =
                    Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(defaultValue = maxStorage, unit = "MB", rule = {
                    it >= 256
                }) { currentMaxStorage ->
                    configManager.updateConfig("maxStorage", currentMaxStorage)
                }
            }
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(15.dp),
                painter = percent(),
                contentDescription = "Cleanup Percentage",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("cleanup_percentage"))

            Spacer(modifier = Modifier.weight(1f))

            val cleanupPercentage by remember { mutableStateOf(configManager.config.cleanupPercentage) }

            Row(
                modifier =
                    Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(defaultValue = cleanupPercentage.toLong(), unit = "%", rule = {
                    it in 10..50
                }) { currentCleanupPercentage ->
                    configManager.updateConfig("cleanupPercentage", currentCleanupPercentage.toInt())
                }
            }
        }
    }
}
