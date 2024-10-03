package com.crosspaste.ui.devices

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import org.koin.compose.koinInject

@Composable
fun NotFoundNearByDevices() {
    val copywriter = koinInject<GlobalCopywriter>()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
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
                text = copywriter.getText("no_other_devices_found_with_crosspaste_enabled"),
                maxLines = 3,
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                    ),
            )
        }
    }
}
