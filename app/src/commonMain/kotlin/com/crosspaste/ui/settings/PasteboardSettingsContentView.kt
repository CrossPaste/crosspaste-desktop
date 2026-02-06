package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Archive
import com.composables.icons.materialsymbols.rounded.Content_paste
import com.composables.icons.materialsymbols.rounded.Music_note
import com.composables.icons.materialsymbols.rounded.Skip_next
import com.composables.icons.materialsymbols.rounded.Stacks
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.paste.PasteboardService
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.Counter
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun PasteboardSettingsContentView(extContent: @Composable () -> Unit = {}) {
    val configManager = koinInject<CommonConfigManager>()
    val pasteboardService = koinInject<PasteboardService>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                SettingListSwitchItem(
                    title = "pasteboard_listening",
                    icon = IconData(MaterialSymbols.Rounded.Content_paste, themeExt.blueIconColor),
                    checked = config.enablePasteboardListening,
                ) {
                    pasteboardService.toggle()
                }
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListSwitchItem(
                    title = "paste_primary_type_only",
                    icon = IconData(MaterialSymbols.Rounded.Stacks, themeExt.amberIconColor),
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
                    icon = IconData(MaterialSymbols.Rounded.Skip_next, themeExt.purpleIconColor),
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
                    icon = IconData(MaterialSymbols.Rounded.Music_note, themeExt.redIconColor),
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
                    icon = IconData(MaterialSymbols.Rounded.Archive, themeExt.greenIconColor),
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
