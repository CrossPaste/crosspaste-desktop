package com.crosspaste.ui.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.crosspaste.cli.CliSymlinkService
import com.crosspaste.cli.ShellAvailability
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import org.koin.compose.koinInject

/**
 * Extension sub-page for the `crosspaste` terminal command: install/repair,
 * where the bundled executable lives, and whether each login shell can
 * resolve the command right now.
 */
@Composable
fun CliToolContentView() {
    val cliSymlinkService = koinInject<CliSymlinkService>()
    val copywriter = koinInject<GlobalCopywriter>()

    val symlinkState by cliSymlinkService.state.collectAsState()

    // Re-probed whenever the symlink state changes (e.g. right after install)
    val shellAvailability by
        produceState<List<ShellAvailability>?>(initialValue = null, symlinkState) {
            value = cliSymlinkService.probeShellAvailability()
        }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                CliToolSettingItem()
            }
        }

        item {
            SectionHeader("executable_path", topPadding = medium)
        }

        item {
            SettingSectionCard {
                Column(
                    modifier = Modifier.padding(medium),
                ) {
                    SelectionContainer {
                        Text(
                            text = cliSymlinkService.cliBinaryPath.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("terminal_availability", topPadding = medium)
        }

        item {
            SettingSectionCard {
                val shells = shellAvailability
                if (shells == null) {
                    ShellRow(shell = "…", statusText = "", available = false)
                } else {
                    shells.forEachIndexed { index, shell ->
                        if (index > 0) {
                            HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                        }
                        ShellRow(
                            shell = shell.shell,
                            statusText =
                                copywriter.getText(
                                    if (shell.available) "available" else "unavailable",
                                ),
                            available = shell.available,
                            resolvedPath = shell.resolvedPath,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellRow(
    shell: String,
    statusText: String,
    available: Boolean,
    resolvedPath: String? = null,
) {
    SettingListItem(
        titleContent = {
            Column {
                Text(
                    text = shell,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                resolvedPath?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        },
    )
}
