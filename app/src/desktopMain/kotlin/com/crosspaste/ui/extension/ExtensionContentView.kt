package com.crosspaste.ui.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Block
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Document_scanner
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.cli.CliSymlinkState
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.MCP
import com.crosspaste.ui.NavigationManager
import com.crosspaste.ui.OCR
import com.crosspaste.ui.SourceControl
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

@Composable
fun ExtensionContentView() {
    val cliSymlinkService = koinInject<CliSymlinkService>()
    val navigateManager = koinInject<NavigationManager>()
    val themeExt = LocalThemeExtState.current

    // Probed once at app startup; NOT_SUPPORTED covers non-macOS platforms
    // and dev runs without the bundled CLI binary
    val cliSymlinkState by cliSymlinkService.state.collectAsState()

    var isProxyExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            ProxySection(
                isExpanded = isProxyExpanded,
                onToggle = { isProxyExpanded = !isProxyExpanded },
            )
        }

        item {
            SectionHeader("extension", topPadding = medium)
        }

        item {
            SettingSectionCard {
                SettingListItem(
                    title = "mcp_settings",
                    subtitle = "mcp_settings_desc",
                    icon =
                        IconData(
                            imageVector = MaterialSymbols.Rounded.Code,
                            iconColor = themeExt.indigoIconColor,
                        ),
                    onClick = {
                        navigateManager.navigate(MCP)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "ocr_settings",
                    subtitle = "language_module_settings",
                    icon =
                        IconData(
                            imageVector = MaterialSymbols.Rounded.Document_scanner,
                            iconColor = themeExt.violetIconColor,
                        ),
                    onClick = {
                        navigateManager.navigate(OCR)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "source_control_settings",
                    subtitle = "source_control_settings_desc",
                    icon =
                        IconData(
                            imageVector = MaterialSymbols.Rounded.Block,
                            iconColor = themeExt.redIconColor,
                        ),
                    onClick = {
                        navigateManager.navigate(SourceControl)
                    },
                )
            }
        }

        // macOS-only `crosspaste` terminal command install/repair (D6 PATH integration)
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
