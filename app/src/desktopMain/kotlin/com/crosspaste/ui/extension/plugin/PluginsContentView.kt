package com.crosspaste.ui.extension.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Extension
import com.composables.icons.materialsymbols.rounded.Folder
import com.composables.icons.materialsymbols.rounded.Refresh
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.plugin.LocalPlugin
import com.crosspaste.plugin.PluginManifest
import com.crosspaste.plugin.PluginProblem
import com.crosspaste.plugin.PluginRepository
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.base.UISupport
import com.crosspaste.ui.settings.SettingListItem
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.huge
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val DISABLED_CONTENT_ALPHA = 0.38f

@Composable
fun PluginsContentView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val pluginRepository = koinInject<PluginRepository>()
    val uiSupport = koinInject<UISupport>()
    val themeExt = LocalThemeExtState.current
    val scope = rememberCoroutineScope()

    val plugins by pluginRepository.plugins.collectAsState()
    var pendingEnable by remember { mutableStateOf<LocalPlugin?>(null) }
    val pluginsDirectory = remember { pluginRepository.pluginsDirectory() }

    // Opening the page picks up plugins copied in since the last scan
    LaunchedEffect(Unit) { pluginRepository.reload() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                SettingListItem(
                    title = "plugin_directory",
                    subtitleContent = {
                        Text(
                            text = pluginsDirectory.toString(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    icon =
                        IconData(
                            imageVector = MaterialSymbols.Rounded.Folder,
                            iconColor = themeExt.amberIconColor,
                        ),
                    onClick = { uiSupport.openFile(pluginsDirectory) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                SettingListItem(
                    title = "plugin_reload",
                    subtitle = "plugin_reload_desc",
                    icon =
                        IconData(
                            imageVector = MaterialSymbols.Rounded.Refresh,
                            iconColor = themeExt.blueIconColor,
                        ),
                    trailingContent = null,
                    onClick = { scope.launch { pluginRepository.reload() } },
                )
            }
        }

        item {
            SectionHeader("installed_plugins", topPadding = medium)
        }

        item {
            SettingSectionCard {
                if (plugins.isEmpty()) {
                    Text(
                        text = copywriter.getText("no_plugins_found"),
                        modifier = Modifier.padding(medium),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                plugins.forEachIndexed { index, plugin ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                    }
                    PluginRow(
                        plugin = plugin,
                        title = plugin.manifest?.displayName(copywriter.language()) ?: plugin.directory.name,
                        status = pluginStatus(plugin, copywriter),
                        iconData =
                            IconData(
                                imageVector = MaterialSymbols.Rounded.Extension,
                                iconColor = themeExt.purpleIconColor,
                            ),
                    ) { enabled ->
                        if (enabled) {
                            pendingEnable = plugin
                        } else {
                            scope.launch { pluginRepository.setEnabled(plugin, false) }
                        }
                    }
                }
            }
        }
    }

    pendingEnable?.let { plugin ->
        EnablePluginDialog(
            manifest = plugin.manifest!!,
            onDismiss = { pendingEnable = null },
            onConfirm = {
                pendingEnable = null
                scope.launch { pluginRepository.setEnabled(plugin, true) }
            },
        )
    }
}

/** Plugin names come from manifests, not i18n keys, so the row is built here rather than with SettingListSwitchItem. */
@Composable
private fun PluginRow(
    plugin: LocalPlugin,
    title: String,
    status: String,
    iconData: IconData,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Toggling stays possible for an enabled plugin with a problem, so it can be turned off
    val toggleable = plugin.manifest != null && (plugin.problem == null || plugin.enabled)
    val textColor =
        if (toggleable) {
            Color.Unspecified
        } else {
            MaterialTheme.colorScheme.onSurface.copy(
                alpha = DISABLED_CONTENT_ALPHA,
            )
        }
    ListItem(
        modifier =
            Modifier
                .heightIn(min = huge)
                .toggleable(
                    value = plugin.enabled,
                    enabled = toggleable,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
        headlineContent = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
        },
        supportingContent = {
            Text(
                text = status,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (plugin.problem != null ||
                        plugin.missingRequirements.isNotEmpty()
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        textColor
                    },
            )
        },
        leadingContent = { iconData.IconContent() },
        trailingContent = {
            Switch(
                modifier = Modifier.scale(0.8f),
                checked = plugin.enabled,
                enabled = toggleable,
                onCheckedChange = null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun pluginStatus(
    plugin: LocalPlugin,
    copywriter: GlobalCopywriter,
): String =
    when (val problem = plugin.problem) {
        is PluginProblem.InvalidManifest -> copywriter.getText("plugin_invalid_manifest", problem.detail)
        is PluginProblem.DuplicateId -> copywriter.getText("plugin_duplicate_id", problem.id)
        is PluginProblem.UnsupportedPlatform -> copywriter.getText("plugin_unsupported_platform", problem.os)
        is PluginProblem.UnsupportedArch -> copywriter.getText("plugin_unsupported_arch", problem.arch)
        is PluginProblem.AppTooOld -> copywriter.getText("plugin_requires_app_version", problem.minAppVersion)
        is PluginProblem.UnsupportedRuntime -> copywriter.getText("plugin_unsupported_runtime", problem.runtime)
        is PluginProblem.UnsupportedOutput -> copywriter.getText("plugin_unsupported_output", problem.output)
        null ->
            if (plugin.missingRequirements.isNotEmpty()) {
                copywriter.getText("plugin_missing_requirements", plugin.missingRequirements.joinToString(", "))
            } else {
                val manifest = plugin.manifest!!
                "${manifest.version} · ${manifest.pasteTypes.joinToString(", ")}"
            }
    }

/** First-enable consent: clipboard items often carry secrets, so the user sees what the plugin declares. */
@Composable
private fun EnablePluginDialog(
    manifest: PluginManifest,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val appSizeValue = LocalAppSizeValueState.current
    val hosts =
        if (manifest.hosts.isEmpty()) {
            copywriter.getText("plugin_no_declared_hosts")
        } else {
            copywriter.getText("plugin_declared_hosts", manifest.hosts.joinToString(", "))
        }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = copywriter.getText("enable_plugin_title", manifest.displayName(copywriter.language())),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(medium),
            ) {
                Text(
                    text = copywriter.getText("enable_plugin_warning"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${manifest.id} ${manifest.version} · ${manifest.pasteTypes.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(text = hosts, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            DialogActionButton(
                text = copywriter.getText("enable"),
                type = DialogButtonType.FILLED,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DialogActionButton(
                text = copywriter.getText("cancel"),
                type = DialogButtonType.TEXT,
                onClick = onDismiss,
            )
        },
    )
}
