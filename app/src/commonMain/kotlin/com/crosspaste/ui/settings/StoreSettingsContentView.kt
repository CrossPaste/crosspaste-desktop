package com.crosspaste.ui.settings

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.crosspaste.app.AppSize
import com.crosspaste.clean.CleanTime
import com.crosspaste.config.ConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.CustomTextSwitch
import com.crosspaste.ui.base.anglesUpDown
import com.crosspaste.ui.base.clock
import com.crosspaste.ui.base.color
import com.crosspaste.ui.base.database
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.hashtag
import com.crosspaste.ui.base.htmlOrRtf
import com.crosspaste.ui.base.image
import com.crosspaste.ui.base.link
import com.crosspaste.ui.base.measureTextWidth
import com.crosspaste.ui.base.percent
import com.crosspaste.ui.base.text
import com.crosspaste.ui.base.trash
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIColors.selectedColor
import com.crosspaste.ui.theme.AppUIFont.SettingsTextStyle
import com.crosspaste.ui.theme.AppUIFont.selectedTextTextStyle
import com.crosspaste.ui.theme.AppUISize.massive
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import com.crosspaste.ui.theme.AppUISize.tiny2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.zero
import com.crosspaste.utils.Quadruple
import com.crosspaste.utils.getFileUtils
import org.koin.compose.koinInject

@Composable
fun StoreSettingsContentView(extContent: @Composable () -> Unit = {}) {
    val density = LocalDensity.current
    val appSize = koinInject<AppSize>()
    val configManager = koinInject<ConfigManager>()
    val pasteDao = koinInject<PasteDao>()
    val copywriter = koinInject<GlobalCopywriter>()

    val fileUtils = getFileUtils()

    var pasteCount: Long? by remember { mutableStateOf(null) }
    var pasteFormatSize: String? by remember { mutableStateOf(null) }

    var textCount: Long? by remember { mutableStateOf(null) }
    var textFormatSize: String? by remember { mutableStateOf(null) }

    var colorCount: Long? by remember { mutableStateOf(null) }
    var colorFormatSize: String? by remember { mutableStateOf(null) }

    var urlCount: Long? by remember { mutableStateOf(null) }
    var urlFormatSize: String? by remember { mutableStateOf(null) }

    var htmlCount: Long? by remember { mutableStateOf(null) }
    var htmlFormatSize: String? by remember { mutableStateOf(null) }

    var rtfCount: Long? by remember { mutableStateOf(null) }
    var rtfFormatSize: String? by remember { mutableStateOf(null) }

    var imageCount: Long? by remember { mutableStateOf(null) }
    var imageFormatSize: String? by remember { mutableStateOf(null) }

    var fileCount: Long? by remember { mutableStateOf(null) }
    var fileFormatSize: String? by remember { mutableStateOf(null) }

    var allOrFavorite by remember { mutableStateOf(true) }

    val refresh: (Boolean) -> Unit = {
        val pasteResourceInfo =
            pasteDao.getPasteResourceInfo(
                if (it) {
                    null
                } else {
                    true
                },
            )
        pasteCount = pasteResourceInfo.pasteCount
        pasteFormatSize = fileUtils.formatBytes(pasteResourceInfo.pasteSize)

        textCount = pasteResourceInfo.textCount
        textFormatSize = fileUtils.formatBytes(pasteResourceInfo.textSize)

        colorCount = pasteResourceInfo.colorCount
        colorFormatSize = fileUtils.formatBytes(pasteResourceInfo.colorSize)

        urlCount = pasteResourceInfo.urlCount
        urlFormatSize = fileUtils.formatBytes(pasteResourceInfo.urlSize)

        htmlCount = pasteResourceInfo.htmlCount
        htmlFormatSize = fileUtils.formatBytes(pasteResourceInfo.htmlSize)

        rtfCount = pasteResourceInfo.rtfCount
        rtfFormatSize = fileUtils.formatBytes(pasteResourceInfo.rtfSize)

        imageCount = pasteResourceInfo.imageCount
        imageFormatSize = fileUtils.formatBytes(pasteResourceInfo.imageSize)

        fileCount = pasteResourceInfo.fileCount
        fileFormatSize = fileUtils.formatBytes(pasteResourceInfo.fileSize)
    }

    LaunchedEffect(Unit) {
        refresh(allOrFavorite)
    }

    var nameMaxWidth by remember { mutableStateOf(massive) }

    val pasteTypes: Array<Quadruple<String, Painter, Long?, String?>> =
        arrayOf(
            Quadruple("pasteboard", hashtag(), pasteCount, pasteFormatSize),
            Quadruple("text", text(), textCount, textFormatSize),
            Quadruple("color", color(), colorCount, colorFormatSize),
            Quadruple("link", link(), urlCount, urlFormatSize),
            Quadruple("html", htmlOrRtf(), htmlCount, htmlFormatSize),
            Quadruple("rtf", htmlOrRtf(), rtfCount, rtfFormatSize),
            Quadruple("image", image(), imageCount, imageFormatSize),
            Quadruple("file", file(), fileCount, fileFormatSize),
        )

    for (property in pasteTypes) {
        nameMaxWidth =
            maxOf(
                nameMaxWidth,
                measureTextWidth(copywriter.getText(property.first), SettingsTextStyle()),
            )
    }

    Column(
        modifier =
            Modifier.wrapContentSize()
                .background(AppUIColors.settingsBackground),
    ) {
        SettingItemsTitleView("store_info")

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(appSize.settingsItemHeight)
                    .padding(horizontal = small2X, vertical = tiny2X),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier =
                    Modifier.widthIn(min = nameMaxWidth + tiny + medium)
                        .wrapContentHeight(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CustomTextSwitch(
                    checked = allOrFavorite,
                    onCheckedChange = { newAllOrFavorite ->
                        allOrFavorite = newAllOrFavorite
                        refresh(allOrFavorite)
                    },
                    checkedText = copywriter.getText("all"),
                    uncheckedText = copywriter.getText("favorite"),
                )
            }

            Row(
                modifier = Modifier.weight(0.2f),
                horizontalArrangement = Arrangement.End,
            ) {
                SettingsText(text = copywriter.getText("count"))
            }

            Row(
                modifier = Modifier.weight(0.3f),
                horizontalArrangement = Arrangement.End,
            ) {
                SettingsText(text = copywriter.getText("size"))
            }
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        pasteTypes.forEachIndexed { index, quadruple ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(appSize.settingsItemHeight)
                        .padding(horizontal = small2X, vertical = tiny2X),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.size(medium),
                    painter = quadruple.second,
                    contentDescription = "pasteboard",
                    tint = MaterialTheme.colorScheme.contentColorFor(AppUIColors.settingsBackground),
                )
                Spacer(modifier = Modifier.width(tiny))

                SettingsText(
                    modifier = Modifier.width(nameMaxWidth),
                    text = copywriter.getText(quadruple.first),
                )

                Row(
                    modifier = Modifier.weight(0.2f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (quadruple.third != null) {
                        SettingsText(text = "${quadruple.third}")
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(xLarge))
                    }
                }

                Row(
                    modifier = Modifier.weight(0.3f),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (quadruple.fourth != null) {
                        SettingsText(text = quadruple.fourth)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(xLarge))
                    }
                }
            }

            if (index != pasteTypes.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))
            }
        }
    }

    extContent()

    val config by configManager.config.collectAsState()

    Column(
        modifier =
            Modifier.wrapContentSize()
                .background(AppUIColors.settingsBackground),
    ) {
        SettingItemsTitleView("auto_cleanup_settings")

        SettingSwitchItemView(
            text = "expiration_cleanup",
            painter = trash(),
            getCurrentSwitchValue = { config.enableExpirationCleanup },
        ) {
            configManager.updateConfig("enableExpirationCleanup", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingItemView(
            painter = clock(),
            text = "image_retention_period",
        ) {
            val selectImageCleanTimeIndex = config.imageCleanTimeIndex

            var imageCleanTimeValue by remember(copywriter.language()) {
                val imageCleanTime = CleanTime.entries[selectImageCleanTimeIndex]
                mutableStateOf("${imageCleanTime.quantity} ${copywriter.getText(imageCleanTime.unit)}")
            }
            val imageCleanTimeWidth = measureTextWidth(imageCleanTimeValue, SettingsTextStyle())

            var showImageCleanTimeMenu by remember { mutableStateOf(false) }

            Row(
                modifier =
                    Modifier
                        .clip(tiny2XRoundedCornerShape)
                        .wrapContentWidth()
                        .clickable {
                            showImageCleanTimeMenu = !showImageCleanTimeMenu
                        }
                        .padding(tiny2X),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.width(imageCleanTimeWidth),
                    text = imageCleanTimeValue,
                    style = selectedTextTextStyle,
                    color = selectedColor,
                )

                Spacer(modifier = Modifier.width(tiny3X))
                Icon(
                    modifier = Modifier.size(medium),
                    painter = anglesUpDown(),
                    contentDescription = "Image expiration time",
                    tint = selectedColor,
                )
            }

            if (showImageCleanTimeMenu) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    offset =
                        IntOffset(
                            with(density) { (-(imageCleanTimeWidth + xxLarge)).roundToPx() },
                            with(density) { (zero).roundToPx() },
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
                        val currentImageCleanTime = CleanTime.entries[index]
                        imageCleanTimeValue =
                            "${currentImageCleanTime.quantity} ${copywriter.getText(currentImageCleanTime.unit)}"
                        showImageCleanTimeMenu = false
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingItemView(
            painter = file(),
            text = "file_retention_period",
        ) {
            val selectFileCleanTimeIndex = config.fileCleanTimeIndex

            var fileCleanTimeValue by remember(copywriter.language(), config) {
                val fileCleanTime = CleanTime.entries[selectFileCleanTimeIndex]
                mutableStateOf("${fileCleanTime.quantity} ${copywriter.getText(fileCleanTime.unit)}")
            }
            val fileCleanTimeWidth = measureTextWidth(fileCleanTimeValue, SettingsTextStyle())

            var showFileCleanTimeMenu by remember { mutableStateOf(false) }

            Row(
                modifier =
                    Modifier
                        .clip(tiny2XRoundedCornerShape)
                        .wrapContentWidth()
                        .clickable {
                            showFileCleanTimeMenu = !showFileCleanTimeMenu
                        }
                        .padding(tiny2X),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.width(fileCleanTimeWidth),
                    text = fileCleanTimeValue,
                    style = selectedTextTextStyle,
                    color = selectedColor,
                )

                Spacer(modifier = Modifier.width(tiny3X))
                Icon(
                    modifier = Modifier.size(medium),
                    painter = anglesUpDown(),
                    contentDescription = "File Expiry Period",
                    tint = selectedColor,
                )
            }

            if (showFileCleanTimeMenu) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    offset =
                        IntOffset(
                            with(density) { (-(fileCleanTimeWidth + xxLarge)).roundToPx() },
                            with(density) { (zero).roundToPx() },
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
                        val currentFileCleanTime = CleanTime.entries[index]
                        fileCleanTimeValue =
                            "${currentFileCleanTime.quantity} ${copywriter.getText(currentFileCleanTime.unit)}"
                        showFileCleanTimeMenu = false
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(small3X))

    Column(
        modifier =
            Modifier.wrapContentSize()
                .background(AppUIColors.settingsBackground),
    ) {
        SettingSwitchItemView(
            text = "threshold_cleanup",
            painter = trash(),
            getCurrentSwitchValue = { config.enableThresholdCleanup },
        ) {
            configManager.updateConfig("enableThresholdCleanup", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingCounterItemView(
            text = "maximum_storage",
            painter = database(),
            unit = "MB",
            rule = { it >= 256 },
            getCurrentCounterValue = { config.maxStorage },
        ) {
            configManager.updateConfig("maxStorage", it)
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingCounterItemView(
            text = "cleanup_percentage",
            painter = percent(),
            unit = "%",
            rule = { it in 10..50 },
            getCurrentCounterValue = { config.cleanupPercentage.toLong() },
        ) {
            configManager.updateConfig("cleanupPercentage", it.toInt())
        }
    }
}
