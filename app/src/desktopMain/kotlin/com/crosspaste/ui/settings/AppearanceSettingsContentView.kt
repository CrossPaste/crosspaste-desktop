package com.crosspaste.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Height
import com.crosspaste.app.DesktopAppSize
import com.crosspaste.app.DesktopAppSize.Companion.MAX_SEARCH_WINDOW_HEIGHT
import com.crosspaste.app.DesktopAppSize.Companion.MIN_SEARCH_WINDOW_HEIGHT
import com.crosspaste.app.DesktopAppWindowManager
import com.crosspaste.config.DesktopConfigManager
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.base.IconData
import com.crosspaste.ui.base.SectionHeader
import com.crosspaste.ui.theme.AppUISize.gigantic
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xxLarge
import com.crosspaste.ui.theme.AppUISize.xxxxLarge
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Composable
fun AppearanceSettingsContentView() {
    val appSize = koinInject<DesktopAppSize>()
    val appWindowManager = koinInject<DesktopAppWindowManager>()
    val configManager = koinInject<DesktopConfigManager>()
    val themeExt = LocalThemeExtState.current

    val config by configManager.config.collectAsState()

    // Incremented on every slider movement; drives the show + auto-hide of the
    // live search window preview
    var previewTick by remember { mutableIntStateOf(0) }

    fun hideSearchWindowPreview() {
        appWindowManager.hideSearchWindowPreview()
    }

    // Auto-hide the preview window 3s after the last slider movement
    LaunchedEffect(previewTick) {
        if (previewTick > 0) {
            delay(3.seconds)
            hideSearchWindowPreview()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Leaving the page dismisses the preview and any un-persisted height
            hideSearchWindowPreview()
            appSize.clearSearchWindowHeightPreview()
            appWindowManager.refreshSearchWindowState()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tiny),
    ) {
        item {
            SettingSectionCard {
                ThemeSettingItem()
                HorizontalDivider(modifier = Modifier.padding(start = xxxxLarge))
                FontSettingItemView()
            }
        }

        item {
            SectionHeader("search_window", topPadding = medium)
        }

        item {
            SettingSectionCard {
                val clampedHeight =
                    config.searchWindowHeight.coerceIn(
                        MIN_SEARCH_WINDOW_HEIGHT,
                        MAX_SEARCH_WINDOW_HEIGHT,
                    )
                var sliderHeight by remember(clampedHeight) { mutableFloatStateOf(clampedHeight.toFloat()) }

                SettingListItem(
                    title = "search_window_height",
                    icon = IconData(MaterialSymbols.Rounded.Height, themeExt.blueIconColor),
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = sliderHeight,
                                onValueChange = { newHeight ->
                                    sliderHeight = newHeight
                                    appSize.previewSearchWindowHeight(newHeight.roundToInt())
                                    appWindowManager.showSearchWindowPreview()
                                    previewTick++
                                },
                                onValueChangeFinished = {
                                    configManager.updateConfig(
                                        "searchWindowHeight",
                                        sliderHeight.roundToInt(),
                                    )
                                },
                                valueRange =
                                    MIN_SEARCH_WINDOW_HEIGHT.toFloat()..MAX_SEARCH_WINDOW_HEIGHT.toFloat(),
                                modifier = Modifier.width(gigantic),
                            )
                            Spacer(modifier = Modifier.width(tiny))
                            Text(
                                text = sliderHeight.roundToInt().toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(xxLarge),
                            )
                        }
                    },
                )
            }
        }
    }
}
