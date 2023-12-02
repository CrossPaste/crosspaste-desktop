package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clipevery.LocalKoinApplication
import com.clipevery.device.DeviceInfoFactory
import com.clipevery.model.HostInfo
import com.clipevery.model.AppInfo
import com.clipevery.model.EndpointInfo
import com.clipevery.model.SyncInfo
import com.clipevery.model.SyncState
import com.clipevery.platform.Platform
import com.clipevery.platform.currentPlatform

@Composable
fun Devices() {
    val current = LocalKoinApplication.current
    val deviceFactory = current.koin.get<DeviceInfoFactory>()
    val deviceInfo = deviceFactory.createDeviceInfo()
    SyncItem(SyncInfo(
        appInfo = AppInfo(
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        endpointInfo = EndpointInfo(
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            platform = currentPlatform(),
            hostInfo = deviceInfo.hostInfoList[0],
            port = 8080
        ),
        state = SyncState.ONLINE)
    )
    Divider(modifier = Modifier.fillMaxWidth())
    SyncItem(SyncInfo(
        appInfo = AppInfo(
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        endpointInfo = EndpointInfo(
            deviceId = "abdcs-adasda-sdasdasd",
            deviceName = "John Doe's Windows",
            platform = object: Platform {
                override val name: String
                    get() = "Windows"
                override val arch: String = "amd64"
                override val bitMode: Int = 64
                override val version: String = "11"
            },
            hostInfo = HostInfo(
                displayName = "wifi",
                hostAddress = "192.168.31.2"
            ),
            port = 8080
        ),
        state = SyncState.OFFLINE)
    )
    Divider(modifier = Modifier.fillMaxWidth())
    SyncItem(SyncInfo(
        appInfo = AppInfo(
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        endpointInfo = EndpointInfo(
            deviceId = "abdcs-adasda-sdasdasd",
            deviceName = "John Doe's Windows",
            hostInfo = HostInfo(
                displayName = "wifi",
                hostAddress = "192.168.31.3"
            ),
            platform = object: Platform {
                override val name: String = "Linux"
                override val arch: String = "amd64"
                override val bitMode: Int = 32
                override val version: String = "20.23"
            },
            port = 8080
        ),
        state = SyncState.UNVERIFIED)
    )
}

@Preview
@Composable
fun test() {
    Column(modifier = Modifier.fillMaxSize()) {
        Devices()
    }
}

