package com.crosspaste.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Wifi_find
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.LocalThemeExtState
import com.crosspaste.ui.theme.AppUISize.enormous
import com.crosspaste.ui.theme.AppUISize.medium
import com.crosspaste.ui.theme.AppUISize.small2X
import com.crosspaste.ui.theme.AppUISize.tiny
import com.crosspaste.ui.theme.AppUISize.tiny3X
import com.crosspaste.ui.theme.AppUISize.xLarge
import com.crosspaste.ui.theme.AppUISize.xLargeRoundedCornerShape
import org.koin.compose.koinInject

@Composable
fun NotFoundNearByDevices(guideContent: (@Composable () -> Unit)? = null) {
    val copyWriter = koinInject<GlobalCopywriter>()
    Surface(
        modifier =
            Modifier
                .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = xLargeRoundedCornerShape,
    ) {
        Column(
            modifier = Modifier.padding(xLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Wifi_find,
                contentDescription = null,
                modifier = Modifier.size(enormous),
                tint = LocalThemeExtState.current.info.color,
            )
            Spacer(modifier = Modifier.height(medium))
            Text(
                text = copyWriter.getText("nearby_devices_not_found"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(tiny))
            Text(
                text = copyWriter.getText("nearby_devices_not_found_desc"),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        lineBreak = LineBreak.Heading,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            guideContent?.invoke()
        }
    }
}

@Composable
fun StoreBadge(
    icon: @Composable () -> Unit,
    topText: String,
    bottomText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = small2X, vertical = tiny),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(medium),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Spacer(modifier = Modifier.width(tiny3X))
            Column {
                Text(
                    text = topText,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                        ),
                    color = MaterialTheme.colorScheme.surface,
                )
                Text(
                    text = bottomText,
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                        ),
                    color = MaterialTheme.colorScheme.surface,
                )
            }
        }
    }
}
