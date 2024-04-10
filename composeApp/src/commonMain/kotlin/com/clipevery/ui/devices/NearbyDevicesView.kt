package com.clipevery.ui.devices

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipevery.LocalKoinApplication
import com.clipevery.dao.sync.SyncRuntimeInfo
import com.clipevery.dao.sync.createSyncRuntimeInfo
import com.clipevery.i18n.GlobalCopywriter
import com.clipevery.sync.DeviceManager
import com.clipevery.ui.PageViewContext
import com.clipevery.ui.base.magnifying
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NearbyDevicesView(currentPageViewContext: MutableState<PageViewContext>) {
    val current = LocalKoinApplication.current
    val deviceManager = current.koin.get<DeviceManager>()
    val isSearching by deviceManager.isSearching

    LaunchedEffect(Unit) {
        deviceManager.toSearchNearBy()
    }

    if (isSearching) {
        SearchNearByDevices()
    } else if (deviceManager.syncInfos.isEmpty()) {
        NotFoundNearByDevices()
    } else {
        ShowNearByDevices()
    }
}

@Composable
fun NotFoundNearByDevices() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = copywriter.getText("No_other_devices_found_with_Clipevery_enabled"),
                color = MaterialTheme.colors.onBackground,
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun SearchNearByDevices() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val deviceManager = current.koin.get<DeviceManager>()

    val isSearching by deviceManager.isSearching
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(isSearching) {
        if (isSearching) {
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
                tint = MaterialTheme.colors.primary,
                modifier = Modifier.size(70.dp),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            Text(
                text = copywriter.getText("Searching_for_nearby_devices"),
                color = MaterialTheme.colors.onBackground,
                fontSize = 28.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun ShowNearByDevices() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val deviceManager = current.koin.get<DeviceManager>()

    val syncInfos = deviceManager.syncInfos

    Column(modifier = Modifier.fillMaxSize()) {
        for ((index, syncInfo) in syncInfos.withIndex()) {
            NearbyDeviceView(createSyncRuntimeInfo(syncInfo))
            if (index != syncInfos.size - 1) {
                Divider(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NearbyDeviceView(syncRuntimeInfo: SyncRuntimeInfo) {
    var hover by remember { mutableStateOf(false) }
    val backgroundColor =
        if (hover) {
            MaterialTheme.colors.secondaryVariant
        } else {
            MaterialTheme.colors.background
        }

    DeviceBarView(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(backgroundColor)
                .onPointerEvent(
                    eventType = PointerEventType.Enter,
                    onEvent = {
                        hover = true
                    },
                ).onPointerEvent(
                    eventType = PointerEventType.Exit,
                    onEvent = {
                        hover = false
                    },
                ),
        syncRuntimeInfo,
    ) {
    }
}
