package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.sync

@Composable
fun PasteboardSettingsView() {
    val current = LocalKoinApplication.current
    val configManager = current.koin.get<ConfigManager>()
    val copywriter = current.koin.get<GlobalCopywriter>()

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("paste_control"),
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
            val maxStorage by remember { mutableStateOf(configManager.config.maxBackupFileSize) }
            val scrollState = rememberScrollState()
            Icon(
                modifier = Modifier.size(15.dp),
                painter = file(),
                contentDescription = "max back up file size",
                tint = Color(0xFF41B06E),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier =
                    Modifier.wrapContentWidth()
                        .horizontalScroll(scrollState),
            ) {
                settingsText(copywriter.getText("max_back_up_file_size"))
            }
            Spacer(modifier = Modifier.weight(1f).widthIn(min = 8.dp))
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(defaultValue = maxStorage, unit = "MB", rule = {
                    it >= 0
                }) { currentMaxStorage ->
                    configManager.updateConfig("maxBackupFileSize", currentMaxStorage)
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
                painter = sync(),
                contentDescription = "sync",
                tint = MaterialTheme.colors.onBackground,
            )

            Spacer(modifier = Modifier.width(8.dp))

            settingsText(copywriter.getText("sync_file_size_limit"))

            var enabledSyncFileSizeLimit by remember {
                mutableStateOf(
                    configManager.config.enabledSyncFileSizeLimit,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = enabledSyncFileSizeLimit,
                onCheckedChange = { newEnabledSyncFileSizeLimit ->
                    configManager.updateConfig("enabledSyncFileSizeLimit", newEnabledSyncFileSizeLimit)
                    enabledSyncFileSizeLimit = configManager.config.enabledSyncFileSizeLimit
                },
            )
        }

        if (configManager.config.enabledSyncFileSizeLimit) {
            Divider(modifier = Modifier.padding(start = 35.dp))

            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(40.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                val maxSyncFileSize by remember { mutableStateOf(configManager.config.maxSyncFileSize) }
                val scrollState = rememberScrollState()
                Icon(
                    modifier = Modifier.size(15.dp),
                    painter = file(),
                    contentDescription = "max sync file size",
                    tint = Color(0xFF41B06E),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier =
                        Modifier.weight(1f)
                            .horizontalScroll(scrollState)
                            .padding(end = 8.dp),
                ) {
                    settingsText(copywriter.getText("max_sync_file_size"))
                }
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Counter(defaultValue = maxSyncFileSize, unit = "MB", rule = {
                        it >= 0
                    }) { currentMaxSyncFileSize ->
                        configManager.updateConfig("maxSyncFileSize", currentMaxSyncFileSize)
                    }
                }
            }
        }
    }
}
