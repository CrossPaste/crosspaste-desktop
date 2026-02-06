package com.crosspaste.ui.base

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Share
import com.crosspaste.share.AppShareService
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.mediumRoundedCornerShape
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny5X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xxLarge
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun ShareContentView() {
    val appShareService = koinInject<AppShareService>()
    val scope = rememberCoroutineScope()
    val shareText by remember { mutableStateOf(appShareService.getShareText()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(xxLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = mediumRoundedCornerShape,
                colors =
                    CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                border = BorderStroke(tiny5X, MaterialTheme.colorScheme.outlineVariant),
            ) {
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

            Spacer(modifier = Modifier.height(xxLarge))

            // Platform grid with 4 columns for better touch targets
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(medium),
                verticalArrangement = Arrangement.spacedBy(xLarge),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
            ) {
                items(appShareService.appSharePlatformList) { platform ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(small2X))
                                .clickable {
                                    scope.launch { platform.action(appShareService) }
                                }.padding(vertical = tiny),
                    ) {
                        platform.ButtonPlatform()

                        Spacer(modifier = Modifier.height(tiny))

                        Text(
                            text = platform.platformName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(xxLarge))
        }
    }
}
