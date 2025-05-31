package com.crosspaste.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.ui.theme.AppUIColors
import com.crosspaste.ui.theme.AppUIFont.emptyScreenTipsTextStyle
import org.koin.compose.koinInject

@Composable
fun NotFoundNearByDevices() {
    val copywriter = koinInject<GlobalCopywriter>()
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.fillMaxSize()
                .background(AppUIColors.deviceBackground),
    ) {
        Box(
            modifier = Modifier.wrapContentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = copywriter.getText("no_nearby_devices_found_with_crosspaste_enabled"),
                maxLines = 3,
                color = MaterialTheme.colorScheme.contentColorFor(AppUIColors.deviceBackground),
                style = emptyScreenTipsTextStyle,
            )
        }
    }
}
