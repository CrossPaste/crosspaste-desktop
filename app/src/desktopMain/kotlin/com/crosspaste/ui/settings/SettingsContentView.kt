package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.cli.CliSymlinkState
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import org.koin.compose.koinInject

@Composable
fun SettingsContentView() {
    val cliSymlinkService = koinInject<CliSymlinkService>()
    val cliSymlinkState by cliSymlinkService.state.collectAsState()

    // The service state starts pessimistic (section hidden) until probed
    LaunchedEffect(Unit) {
        cliSymlinkService.refresh()
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SectionHeader("general")
        }

        item {
            MainSettingsContentView()
        }

        item {
            SectionHeader("advanced", topPadding = medium)
        }

        item {
            AdvancedSettingsContentView()
        }

        // macOS-only terminal command install/repair (D6 PATH integration);
        // NOT_SUPPORTED covers other platforms and dev runs without the
        // bundled CLI binary.
        if (cliSymlinkState != CliSymlinkState.NOT_SUPPORTED) {
            item {
                SectionHeader("command_line", topPadding = medium)
            }

            item {
                SettingSectionCard {
                    CliToolSettingItem()
                }
            }
        }
    }
}
