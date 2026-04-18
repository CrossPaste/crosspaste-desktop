package com.crosspaste.ui.extension.sourcecontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.scale
import com.crosspaste.app.AppInfo
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.paste.DesktopSourceExclusionService
import com.crosspaste.ui.base.AppSourceIcon
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun SourceControlContentView() {
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDao = koinInject<PasteDao>()
    val sourceExclusionService = koinInject<DesktopSourceExclusionService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()

    val config by configManager.config.collectAsState()

    val exclusions =
        remember(config.sourceExclusions) {
            sourceExclusionService.getExclusions()
        }

    var allSources by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(exclusions) {
        allSources =
            withContext(ioDispatcher) {
                coroutineScope {
                    val dbDeferred = async { pasteDao.getDistinctSources() }
                    val runningDeferred = async { appWindowManager.getRunningAppNames() }
                    (dbDeferred.await() + runningDeferred.await() + exclusions).distinct().sorted()
                }
            }
    }

    when (val sources = allSources) {
        null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(tiny),
            ) {
                if (sources.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(medium),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = copywriter.getText("no_source_found"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    item {
                        SettingSectionCard {
                            sources.forEachIndexed { index, source ->
                                val isEnabled = source !in exclusions
                                SourceControlItem(
                                    source = source,
                                    isEnabled = isEnabled,
                                    onToggle = { enabled ->
                                        if (enabled) {
                                            sourceExclusionService.removeExclusion(source)
                                        } else {
                                            sourceExclusionService.addExclusion(source)
                                        }
                                    },
                                )
                                if (index < sources.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceControlItem(
    source: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val appInfo = koinInject<AppInfo>()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = medium, vertical = tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(medium),
    ) {
        AppSourceIcon(
            source = source,
            appInstanceId = appInfo.appInstanceId,
            size = xxxLarge,
        )
        Text(
            text = source,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            modifier = Modifier.scale(0.7f),
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}
