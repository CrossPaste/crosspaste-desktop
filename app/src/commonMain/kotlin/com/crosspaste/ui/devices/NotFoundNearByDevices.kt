package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

@Composable
fun NotFoundNearByDevices() {
    val copywriter = koinInject<GlobalCopywriter>()
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Box(
            modifier = Modifier.wrapContentSize().align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f),
                textAlign = TextAlign.Center,
                text = copywriter.getText("no_nearby_devices_found_with_crosspaste_enabled"),
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
