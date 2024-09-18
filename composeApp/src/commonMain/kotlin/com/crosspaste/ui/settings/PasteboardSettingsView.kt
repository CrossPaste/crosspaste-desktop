package com.crosspaste.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.skipForward
import com.crosspaste.ui.base.sync
import org.koin.compose.koinInject

@Composable
fun PasteboardSettingsView() {
    val configManager = koinInject<ConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 32.dp, top = 5.dp, bottom = 5.dp),
        text = copywriter.getText("paste_control"),
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineSmall,
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
    )

    Column(
        modifier =
            Modifier.wrapContentSize()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background),
    ) {
        SettingItemView(
            painter = skipForward(),
            text = "skip_prior_pasteboard_content",
            tint = Color(0xFFFF885B),
        ) {
            var enableSkipPriorPasteboardContent by remember {
                mutableStateOf(
                    configManager.config.enableSkipPriorPasteboardContent,
                )
            }

            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = enableSkipPriorPasteboardContent,
                onCheckedChange = { newEnableSkipPriorPasteboardContent ->
                    configManager.updateConfig("enableSkipPriorPasteboardContent", newEnableSkipPriorPasteboardContent)
                    enableSkipPriorPasteboardContent = configManager.config.enableSkipPriorPasteboardContent
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingItemView(
            painter = file(),
            text = "max_back_up_file_size",
            tint = Color(0xFF41B06E),
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val maxStorage by remember { mutableStateOf(configManager.config.maxBackupFileSize) }

                Counter(defaultValue = maxStorage, unit = "MB", rule = {
                    it >= 0
                }) { currentMaxStorage ->
                    configManager.updateConfig("maxBackupFileSize", currentMaxStorage)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingItemView(
            painter = sync(),
            text = "sync_file_size_limit",
            tint = Color(0xFF825B32),
        ) {
            var enabledSyncFileSizeLimit by remember {
                mutableStateOf(
                    configManager.config.enabledSyncFileSizeLimit,
                )
            }

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
            HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

            SettingItemView(
                painter = file(),
                text = "max_sync_file_size",
                tint = Color(0xFF41B06E),
            ) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val maxSyncFileSize by remember { mutableStateOf(configManager.config.maxSyncFileSize) }

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
