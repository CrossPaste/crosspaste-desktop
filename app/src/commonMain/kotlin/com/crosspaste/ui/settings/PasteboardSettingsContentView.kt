package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Start
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun PasteboardSettingsContentView(extContent: @Composable () -> Unit = {}) {
    val configManager = koinInject<CommonConfigManager>()
    val pasteboardService = koinInject<PasteboardService>()

    val config by configManager.config.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "pasteboard_listening",
                    icon = Icons.Default.ContentPasteGo,
                    checked = config.enablePasteboardListening,
                ) {
                    pasteboardService.toggle()
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "paste_primary_type_only",
                    icon = Icons.Default.FormatPaint,
                    checked = config.pastePrimaryTypeOnly,
                ) { newPastePrimaryTypeOnly ->
                    configManager.updateConfig(
                        "pastePrimaryTypeOnly",
                        newPastePrimaryTypeOnly,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "skip_pre_launch_pasteboard_content",
                    icon = Icons.Default.Start,
                    checked = config.enableSkipPreLaunchPasteboardContent,
                ) { newEnableSkipPreLaunchPasteboardContent ->
                    configManager.updateConfig(
                        "enableSkipPreLaunchPasteboardContent",
                        newEnableSkipPreLaunchPasteboardContent,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "sound_effect",
                    icon = Icons.Default.MusicNote,
                    checked = config.enableSoundEffect,
                ) { enableSoundEffect ->
                    configManager.updateConfig(
                        "enableSoundEffect",
                        enableSoundEffect,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "max_back_up_file_size",
                    icon = Icons.Default.Archive,
                    trailingContent = {
                        Counter(defaultValue = config.maxBackupFileSize, unit = "MB", rule = {
                            it >= 0
                        }) { currentMaxStorage ->
                            configManager.updateConfig("maxBackupFileSize", currentMaxStorage)
                        }
                    },
                )
                extContent()
            }
        }
    }
}
