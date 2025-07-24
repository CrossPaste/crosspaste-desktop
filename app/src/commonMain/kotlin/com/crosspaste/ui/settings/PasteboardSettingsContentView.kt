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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.CustomSwitch
import com.crosspaste.ui.base.clipboard
import com.crosspaste.ui.base.file
import com.crosspaste.ui.base.skipForward
import com.crosspaste.ui.base.sync
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import org.koin.compose.koinInject

@Composable
fun PasteboardSettingsContentView(extContent: @Composable () -> Unit = {}) {
    val configManager = koinInject<CommonConfigManager>()

    val config by configManager.config.collectAsState()

    Column(
        modifier =
            Modifier
                .wrapContentSize()
                .background(AppUIColors.generalBackground),
    ) {
        SettingItemsTitleView("paste_control")

        SettingItemView(
            painter = clipboard(),
            text = "paste_primary_type_only",
        ) {
            CustomSwitch(
                modifier =
                    Modifier
                        .width(medium * 2)
                        .height(large2X),
                checked = config.pastePrimaryTypeOnly,
                onCheckedChange = { newPastePrimaryTypeOnly ->
                    configManager.updateConfig(
                        "pastePrimaryTypeOnly",
                        newPastePrimaryTypeOnly,
                    )
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingItemView(
            painter = skipForward(),
            text = "skip_pre_launch_pasteboard_content",
        ) {
            CustomSwitch(
                modifier =
                    Modifier
                        .width(medium * 2)
                        .height(large2X),
                checked = config.enableSkipPreLaunchPasteboardContent,
                onCheckedChange = { newEnableSkipPreLaunchPasteboardContent ->
                    configManager.updateConfig(
                        "enableSkipPreLaunchPasteboardContent",
                        newEnableSkipPreLaunchPasteboardContent,
                    )
                },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

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

        HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

        SettingItemView(
            painter = sync(),
            text = "sync_file_size_limit",
        ) {
            CustomSwitch(
                modifier =
                    Modifier
                        .width(medium * 2)
                        .height(large2X),
                checked = config.enabledSyncFileSizeLimit,
                onCheckedChange = { newEnabledSyncFileSizeLimit ->
                    configManager.updateConfig("enabledSyncFileSizeLimit", newEnabledSyncFileSizeLimit)
                },
            )
        }

        if (config.enabledSyncFileSizeLimit) {
            HorizontalDivider(modifier = Modifier.padding(start = xxxLarge))

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
