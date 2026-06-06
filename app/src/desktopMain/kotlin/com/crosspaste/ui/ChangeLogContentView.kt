package com.crosspaste.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.crosspaste.app.AppUpdateService
import com.crosspaste.app.ChangelogEntry
import com.crosspaste.app.ChangelogService
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small
import com.crosspaste.ui.theme.AppUISize.small3X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny2X
import org.koin.compose.koinInject

@Composable
fun ChangeLogContentView() {
    val changelogService = koinInject<ChangelogService>()
    val appUpdateService = koinInject<AppUpdateService>()
    val configManager = koinInject<DesktopConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()

    val currentVersion by appUpdateService.currentVersion.collectAsState()

    val entries = remember { changelogService.loadEntries() }

    // Mark the changelog as seen when the screen is opened, clearing the menu badge.
    // Skip the write when nothing changed so reopening the page doesn't re-persist config.
    LaunchedEffect(currentVersion) {
        val current = currentVersion.toString()
        if (configManager.config.value.lastSeenChangelogVersion != current) {
            configManager.updateConfig("lastSeenChangelogVersion", current)
        }
    }

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = copywriter.getText("change_log_desc"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var showAll by remember { mutableStateOf(false) }

    val primaryEntry =
        entries.firstOrNull { it.version == currentVersion.toString() } ?: entries.first()
    val restEntries = entries.filter { it !== primaryEntry }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(medium),
    ) {
        ChangelogEntryView(primaryEntry)

        if (showAll) {
            restEntries.forEach { entry ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ChangelogEntryView(entry)
            }
        }

        if (restEntries.isNotEmpty()) {
            TextButton(onClick = { showAll = !showAll }) {
                Text(
                    text =
                        copywriter.getText(
                            if (showAll) "changelog_show_less" else "changelog_view_full",
                        ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ChangelogEntryView(entry: ChangelogEntry) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(small),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(tiny),
        ) {
            Text(
                text = "v${entry.version}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = tiny2X),
            )
        }

        entry.body.forEach { line ->
            ChangelogLine(line)
        }
    }
}

@Composable
private fun ChangelogLine(line: String) {
    when {
        line.isBlank() -> {
            Spacer(modifier = Modifier.height(tiny))
        }
        line.startsWith("## ") -> {
            Text(
                text = parseInline(line.removePrefix("## ").trim()),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        line.startsWith("- ") -> {
            Row(horizontalArrangement = Arrangement.spacedBy(tiny)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = parseInline(line.removePrefix("- ").trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            Text(
                text = parseInline(line.trim()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = small3X),
            )
        }
    }
}

/** Renders the small markdown subset used in the bundled changelog: `**bold**` spans. */
private fun parseInline(text: String): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val start = text.indexOf("**", index)
            if (start < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, start))
            val end = text.indexOf("**", start + 2)
            if (end < 0) {
                append(text.substring(start))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(text.substring(start + 2, end))
            }
            index = end + 2
        }
    }
