package com.crosspaste.ui.extension.sourceexclusion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import coil3.PlatformContext
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.CommonConfigManager
import com.crosspaste.db.paste.PasteDao
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.image.coil.AppSourceItem
import com.crosspaste.image.coil.ImageLoaders
import com.crosspaste.paste.SourceExclusionService
import com.crosspaste.ui.base.IconStyle
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUISize.large2X
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import com.crosspaste.utils.ioDispatcher
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun SourceExclusionContentView() {
    val configManager = koinInject<CommonConfigManager>()
    val copywriter = koinInject<GlobalCopywriter>()
    val pasteDao = koinInject<PasteDao>()
    val sourceExclusionService = koinInject<SourceExclusionService>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()

    val config by configManager.config.collectAsState()

    val exclusions =
        remember(config.sourceExclusions) {
            sourceExclusionService.getExclusions()
        }

    var dbSources by remember { mutableStateOf<List<String>>(emptyList()) }
    var runningSources by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        dbSources = pasteDao.getDistinctSources()
    }

    LaunchedEffect(Unit) {
        withContext(ioDispatcher) {
            runningSources = appWindowManager.getRunningAppNames()
        }
    }

    val allSources =
        remember(dbSources, runningSources, exclusions) {
            (dbSources + runningSources + exclusions).distinct().sorted()
        }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        if (allSources.isEmpty()) {
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
                    allSources.forEachIndexed { index, source ->
                        val isEnabled = source !in exclusions
                        SourceExclusionItem(
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
                        if (index < allSources.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceExclusionItem(
    source: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = medium, vertical = tiny),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(medium),
    ) {
        SourceAppIcon(source = source)
        Text(
            text = source,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun SourceAppIcon(source: String) {
    val iconStyle = koinInject<IconStyle>()
    val imageLoaders = koinInject<ImageLoaders>()
    val platformContext = koinInject<PlatformContext>()
    val density = LocalDensity.current

    val size = large2X

    val visualScale =
        remember(source) {
            if (iconStyle.isMacStyleIcon(source)) {
                val paddingRatio = 0.075f
                val contentRatio = 1f - (paddingRatio * 2)
                1f / contentRatio
            } else {
                1f
            }
        }

    val sizePx = with(density) { size.roundToPx() }

    val model =
        remember(source, platformContext, sizePx) {
            ImageRequest
                .Builder(platformContext)
                .data(AppSourceItem(source))
                .size(sizePx)
                .precision(Precision.INEXACT)
                .scale(Scale.FILL)
                .crossfade(true)
                .build()
        }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            modifier =
                Modifier
                    .fillMaxSize()
                    .scale(visualScale),
            model = model,
            imageLoader = imageLoaders.appSourceLoader,
            contentDescription = source,
            contentScale = ContentScale.Fit,
        ) {
            val state by painter.state.collectAsState()
            when (state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error,
                -> {
                    // Empty placeholder when icon not available
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}
