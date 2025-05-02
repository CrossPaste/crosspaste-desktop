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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crosspaste.config.ConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.skipForward
import com.crosspaste.ui.base.sync
import org.koin.compose.koinInject

@Composable
fun PasteboardSettingsContentView(extContent: @Composable () -> Unit = {}) {
    val configManager = koinInject<ConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    val config by configManager.config.collectAsState()

    Text(
        modifier =
            Modifier.wrapContentSize()
                .padding(start = 16.dp, top = 12.dp, bottom = 5.dp),
        text = copywriter.getText("paste_control"),
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.titleSmall,
    )

    Column(
        modifier =
            Modifier.wrapContentSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        SettingItemView(
            painter = skipForward(),
            text = "skip_pre_launch_pasteboard_content",
        ) {
            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = config.enableSkipPreLaunchPasteboardContent,
                onCheckedChange = { newEnableSkipPreLaunchPasteboardContent ->
                    configManager.updateConfig(
                        "enableSkipPreLaunchPasteboardContent",
                        newEnableSkipPreLaunchPasteboardContent,
                    )
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

        SettingItemView(
            painter = file(),
            text = "max_back_up_file_size",
        ) {
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(defaultValue = config.maxBackupFileSize, unit = "MB", rule = {
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
        ) {
            CustomSwitch(
                modifier =
                    Modifier.width(32.dp)
                        .height(20.dp),
                checked = config.enabledSyncFileSizeLimit,
                onCheckedChange = { newEnabledSyncFileSizeLimit ->
                    configManager.updateConfig("enabledSyncFileSizeLimit", newEnabledSyncFileSizeLimit)
                },
            )
        }

        if (config.enabledSyncFileSizeLimit) {
            HorizontalDivider(modifier = Modifier.padding(start = 35.dp))

            SettingItemView(
                painter = file(),
                text = "max_sync_file_size",
            ) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Counter(defaultValue = config.maxSyncFileSize, unit = "MB", rule = {
                        it >= 0
                    }) { currentMaxSyncFileSize ->
                        configManager.updateConfig("maxSyncFileSize", currentMaxSyncFileSize)
                    }
                }
            }
        }

        extContent()
    }
}
