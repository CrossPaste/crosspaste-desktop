package com.crosspaste.ui.extension.sourcecontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.crosspaste.app.AppInfo
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalAppSizeValueState
import com.crosspaste.ui.base.AppSourceIcon
import com.crosspaste.ui.base.DialogActionButton
import com.crosspaste.ui.base.DialogButtonType
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxxLarge
import com.crosspaste.utils.ioDispatcher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private val logger = KotlinLogging.logger {}

/**
 * Lets the user exclude an app without knowing where it lives on disk: lists
 * running apps and every source already seen in the history (minus the ones
 * already excluded), with a "browse" escape hatch for anything else. This is
 * the only route for apps whose binaries a file dialog cannot reach, such as
 * Microsoft Store installs.
 */
@Composable
fun SourceExclusionPickerDialog(
    exclusions: List<String>,
    onPick: (String) -> Unit,
    onBrowse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDao = koinInject<PasteDao>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val appInfo = koinInject<AppInfo>()
    val appSizeValue = LocalAppSizeValueState.current

    var candidates by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(exclusions) {
        // Any failure (a locked database, a window enumeration error) must end
        // the spinner with an empty list rather than leave the dialog loading forever.
        candidates =
            withContext(ioDispatcher) {
                runCatching {
                    coroutineScope {
                        val dbDeferred = async { pasteDao.getDistinctSources() }
                        val runningDeferred = async { appWindowManager.getRunningAppNames() }
                        (dbDeferred.await() + runningDeferred.await())
                            .distinct()
                            .filter { it !in exclusions }
                            .sorted()
                    }
                }.getOrElse { e ->
                    if (e is CancellationException) throw e
                    logger.warn(e) { "Failed to load source exclusion candidates" }
                    emptyList()
                }
            }
    }

    AlertDialog(
        modifier = Modifier.width(appSizeValue.dialogWidth),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = tiny),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(xLarge),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(medium))
                Text(
                    text = copywriter.getText("source_exclusion_pick_title"),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = tiny),
                verticalArrangement = Arrangement.spacedBy(medium),
            ) {
                Text(
                    text = copywriter.getText("source_exclusion_pick_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (val list = candidates) {
                    null -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(medium),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        if (list.isEmpty()) {
                            Text(
                                text = copywriter.getText("source_exclusion_no_candidates"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = gigantic * 2)
                                        .clip(small2XRoundedCornerShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            ) {
                                items(list, key = { it }) { source ->
                                    CandidateRow(
                                        source = source,
                                        appInstanceId = appInfo.appInstanceId,
                                        onClick = { onPick(source) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionButton(
                text = copywriter.getText("source_exclusion_browse"),
                type = DialogButtonType.TONAL,
                onClick = onBrowse,
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

@Composable
private fun CandidateRow(
    source: String,
    appInstanceId: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = medium, vertical = tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(medium),
    ) {
        AppSourceIcon(
            source = source,
            appInstanceId = appInstanceId,
            size = xxxLarge,
        )
        Text(
            text = source,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}
