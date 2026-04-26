package com.crosspaste.ui.mouse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.mouse.IpcEvent
import com.crosspaste.mouse.MouseDaemonManager
import com.crosspaste.mouse.MouseState
import com.crosspaste.ui.theme.AppUISize
import org.koin.compose.koinInject

/**
 * Settings screen for the mouse-sharing feature: exposes the on/off switch,
 * a one-line status summary driven by [MouseDaemonManager.state], the drag-to-
 * arrange [ScreenCanvas], and a permission dialog that surfaces any current
 * warning emitted by the daemon.
 */
@Composable
fun MouseSettingsScreen() {
    val manager: MouseDaemonManager = koinInject()
    val viewModel: ScreenArrangementViewModel = koinInject()
    val configManager: DesktopConfigManager = koinInject()
    val copywriter = koinInject<GlobalCopywriter>()

    LaunchedEffect(viewModel) { viewModel.observe() }

    val state by manager.state.collectAsState()
    val config by configManager.config.collectAsState()

    val currentWarning =
        (state as? MouseState.Warning)?.let { IpcEvent.Warning(it.code, it.message) }

    // Allow the user to acknowledge-and-hide a warning even while the manager
    // still carries it on `state` (e.g. daemon hasn't yet re-emitted a cleared
    // status). The flag auto-resets whenever the warning identity changes.
    var dismissed by remember(currentWarning) { mutableStateOf(false) }

    MousePermissionDialog(
        warning = currentWarning?.takeUnless { dismissed },
        onDismiss = { dismissed = true },
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(AppUISize.medium),
        verticalArrangement = Arrangement.spacedBy(AppUISize.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                copywriter.getText("mouse_settings.switch"),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = config.mouseEnabled,
                onCheckedChange = { configManager.updateConfig("mouseEnabled", it) },
            )
        }
        Text(
            text =
                when (val s = state) {
                    MouseState.Disabled -> copywriter.getText("mouse_settings.state.disabled")
                    MouseState.Starting -> copywriter.getText("mouse_settings.state.starting")
                    is MouseState.Running ->
                        copywriter.getText(
                            "mouse_settings.state.running_n_peers",
                            s.connectedPeers.size,
                        )
                    is MouseState.Warning ->
                        copywriter.getText("mouse_settings.state.warning_prefix", s.code)
                    is MouseState.Error ->
                        copywriter.getText("mouse_settings.state.error_prefix", s.message)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(AppUISize.small))
        ScreenCanvas(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
