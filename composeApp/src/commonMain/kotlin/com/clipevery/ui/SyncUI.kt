package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clipevery.LocalKoinApplication
import com.clipevery.endpoint.EndpointInfoFactory
import com.clipevery.net.HostInfo
import com.clipevery.app.AppInfo
import com.clipevery.endpoint.ExplicitEndpointInfo
import com.clipevery.model.sync.SyncInfo
import com.clipevery.model.sync.SyncState
import com.clipevery.platform.Platform
import com.clipevery.platform.currentPlatform

@Composable
fun Syncs() {
    val current = LocalKoinApplication.current
    val deviceFactory = current.koin.get<EndpointInfoFactory>()
    val deviceInfo = deviceFactory.createEndpointInfo()
    SyncItem(
        SyncInfo(
        appInfo = AppInfo(
            appInstanceId = "1234567890",
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        endpointInfo = ExplicitEndpointInfo(
            deviceId = deviceInfo.deviceId,
            deviceName = deviceInfo.deviceName,
            platform = currentPlatform(),
            hostInfo = deviceInfo.hostInfoList[0],
            port = 8080
        ),
        state = SyncState.ONLINE)
    )
    Divider(modifier = Modifier.fillMaxWidth())
    SyncItem(
        SyncInfo(
        appInfo = AppInfo(
            appInstanceId = "1234567890",
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        endpointInfo = ExplicitEndpointInfo(
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
    SyncItem(
        SyncInfo(
        appInfo = AppInfo(
            appInstanceId = "1234567890",
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        endpointInfo = ExplicitEndpointInfo(
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
        Syncs()
    }
}

