package com.crosspaste.ui.devices

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crosspaste.LocalKoinApplication
import com.crosspaste.config.ConfigManager
import com.crosspaste.dao.sync.SyncRuntimeInfoDao
import com.crosspaste.dao.sync.createSyncRuntimeInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.sync.DeviceManager
import com.crosspaste.ui.base.magnifying
import com.crosspaste.ui.connectedColor
import com.crosspaste.ui.disconnectedColor
import com.crosspaste.ui.selectColor
import com.crosspaste.utils.getJsonUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

@Composable
fun NearbyDevicesView() {
    val current = LocalKoinApplication.current
    val deviceManager = current.koin.get<DeviceManager>()

    val nearbyDevicesList = remember { deviceManager.syncInfos }

    if (deviceManager.searching) {
        SearchNearByDevices()
    } else if (nearbyDevicesList.isEmpty()) {
        NotFoundNearByDevices()
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            for ((index, syncInfo) in nearbyDevicesList.withIndex()) {
                NearbyDeviceView(syncInfo)
                if (index != nearbyDevicesList.size - 1) {
                    Divider(modifier = Modifier.fillMaxWidth())
                }
            }
        }
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
        Box(
            modifier = Modifier.wrapContentSize().align(Alignment.Center),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f),
                textAlign = TextAlign.Center,
                text = copywriter.getText("No_other_devices_found_with_CrossPaste_enabled"),
                maxLines = 3,
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                    ),
            )
        }
    }
}

@Composable
fun SearchNearByDevices() {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val deviceManager = current.koin.get<DeviceManager>()

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(deviceManager.searching) {
        if (deviceManager.searching) {
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
                modifier =
                    Modifier.align(Alignment.CenterHorizontally)
                        .fillMaxWidth(0.8f),
                textAlign = TextAlign.Center,
                text = copywriter.getText("Searching_for_nearby_devices"),
                color = MaterialTheme.colors.onBackground,
                fontSize = 28.sp,
                maxLines = 3,
                lineHeight = 32.sp,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SyncDeviceView(
    syncInfo: SyncInfo,
    action: @Composable () -> Unit,
) {
    val syncRuntimeInfo = createSyncRuntimeInfo(syncInfo)

    var hover by remember { mutableStateOf(false) }
    val backgroundColor =
        if (hover) {
            MaterialTheme.colors.selectColor()
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
        Row(
            modifier =
                Modifier.wrapContentSize()
                    .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            action()
        }
    }
}

@Composable
fun NearbyDeviceView(syncInfo: SyncInfo) {
    val current = LocalKoinApplication.current
    val copywriter = current.koin.get<GlobalCopywriter>()
    val deviceManager = current.koin.get<DeviceManager>()
    val syncRuntimeInfoDao = current.koin.get<SyncRuntimeInfoDao>()
    val configManager = current.koin.get<ConfigManager>()
    val jsonUtils = getJsonUtils()
    SyncDeviceView(syncInfo = syncInfo) {
        Button(
            modifier = Modifier.height(28.dp),
            onClick = {
                val newSyncRuntimeInfo = createSyncRuntimeInfo(syncInfo)
                syncRuntimeInfoDao.insertOrUpdate(newSyncRuntimeInfo)
            },
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, connectedColor()),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
            elevation =
                ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                text = copywriter.getText("Add"),
                color = connectedColor(),
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                    ),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            modifier = Modifier.height(28.dp),
            onClick = {
                val blackSyncInfos: MutableList<SyncInfo> = jsonUtils.JSON.decodeFromString(configManager.config.blacklist)
                for (blackSyncInfo in blackSyncInfos) {
                    if (blackSyncInfo.appInfo.appInstanceId == syncInfo.appInfo.appInstanceId) {
                        return@Button
                    }
                }
                blackSyncInfos.add(syncInfo)
                val newBlackList = jsonUtils.JSON.encodeToString(blackSyncInfos)
                configManager.updateConfig { it.copy(blacklist = newBlackList) }
                deviceManager.refresh()
            },
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, disconnectedColor()),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.background),
            elevation =
                ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
        ) {
            Text(
                text = copywriter.getText("Block"),
                color = disconnectedColor(),
                style =
                    TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Light,
                        fontSize = 14.sp,
                    ),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
    }
}
