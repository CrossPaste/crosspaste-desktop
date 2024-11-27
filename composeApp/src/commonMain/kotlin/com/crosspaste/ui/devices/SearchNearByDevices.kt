package com.crosspaste.ui.devices

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.DeviceManager
import com.crosspaste.ui.base.magnifying
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SearchNearByDevices() {
    val copywriter = koinInject<GlobalCopywriter>()
    val deviceManager = koinInject<DeviceManager>()

    val searching by deviceManager.searching.collectAsState()

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(searching) {
        if (searching) {
            while (true) {
                launch {
                    offsetX.animateTo(
                        targetValue = (-20..20).random().toFloat(),
                        animationSpec = tween(durationMillis = 500),
                    )
                }
                launch {
                    offsetY.animateTo(
                        targetValue = (-20..20).random().toFloat(),
                        animationSpec = tween(durationMillis = 500),
                    )
                }
                delay(500)
            }
        } else {
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .offset(x = offsetX.value.dp, y = offsetY.value.dp - 48.dp),
        ) {
            Icon(
                painter = magnifying(),
                contentDescription = "Searching",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(70.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            Text(
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.8f),
                textAlign = TextAlign.Center,
                text = copywriter.getText("searching_for_nearby_devices"),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                maxLines = 3,
                lineHeight = 32.sp,
            )
        }
    }
}
