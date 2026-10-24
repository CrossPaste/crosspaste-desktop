package com.crosspaste.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.KeyboardView
import com.crosspaste.ui.base.nonFocusable
import com.crosspaste.ui.model.PluginActionsViewModel
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont
import com.crosspaste.ui.theme.AppUISize.large
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tinyRoundedCornerShape
import org.koin.compose.koinInject

/** The shortcut that opens the plugin panel, shown only while some plugin can run. */
@Composable
fun PluginActionsHintView() {
    val copywriter = koinInject<GlobalCopywriter>()
    val platform = koinInject<Platform>()
    val pluginActionsViewModel = koinInject<PluginActionsViewModel>()

    val hasRunnablePlugins by pluginActionsViewModel.hasRunnablePlugins.collectAsState()
    if (!hasRunnablePlugins) return

    val shortcut = remember { if (platform.isMacos()) "⌘K" else "Ctrl+K" }

    Spacer(modifier = Modifier.width(large))
    Row(
        modifier =
            Modifier
                .wrapContentSize()
                .clip(tinyRoundedCornerShape)
                .nonFocusable()
                .clickable { pluginActionsViewModel.toggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyboardView(key = shortcut)
        Spacer(modifier = Modifier.width(tiny))
        Text(
            text = copywriter.getText("plugins"),
            style =
                AppUIFont.tipsTextStyle.copy(
                    color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.generalBackground),
                ),
        )
    }
}
