package com.crosspaste.ui.base

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Share
import com.crosspaste.share.AppSharePlatform
import com.crosspaste.share.AppShareService
import com.crosspaste.ui.settings.SettingSectionCard
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2XRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val PLATFORM_COLUMNS = 4

@Composable
fun ShareContentView() {
    val appShareService = koinInject<AppShareService>()
    val scope = rememberCoroutineScope()
    val shareText by remember { mutableStateOf(appShareService.getShareText()) }

    InnerScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(xxLarge),
        ) {
            SettingSectionCard {
                Box(modifier = Modifier.padding(medium)) {
                    Text(
                        text = shareText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = xxLarge),
                    )

                    Icon(
                        modifier = Modifier.align(Alignment.TopEnd).size(xLarge),
                        imageVector = MaterialSymbols.Rounded.Share,
                        contentDescription = "Copy text",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            SettingSectionCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(medium),
                    verticalArrangement = Arrangement.spacedBy(xLarge),
                ) {
                    appShareService.appSharePlatformList.chunked(PLATFORM_COLUMNS).forEach { rowPlatforms ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(medium),
                        ) {
                            rowPlatforms.forEach { platform ->
                                PlatformItem(
                                    modifier = Modifier.weight(1f),
                                    platform = platform,
                                ) {
                                    scope.launch { platform.action(appShareService) }
                                }
                            }
                            repeat(PLATFORM_COLUMNS - rowPlatforms.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformItem(
    modifier: Modifier = Modifier,
    platform: AppSharePlatform,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(small2XRoundedCornerShape)
                .clickable(onClick = onClick)
                .padding(vertical = tiny),
    ) {
        platform.ButtonPlatform()

        Spacer(modifier = Modifier.height(tiny))

        Text(
            text = platform.platformName,
            style = MaterialTheme.typography.labelMedium,
            color = AppUIColors.sectionTitleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
