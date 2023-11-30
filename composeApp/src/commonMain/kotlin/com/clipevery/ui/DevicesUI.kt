package com.clipevery.ui

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.clipevery.model.AppHostInfo
import com.clipevery.model.AppInfo
import com.clipevery.model.DeviceInfo
import com.clipevery.model.DeviceState
import com.clipevery.platform.Platform
import com.clipevery.platform.currentPlatform

@Composable
fun Devices() {
    DeviceItem(DeviceInfo(
        deviceId = "abdcs-adasda-sdasdasd",
        appInfo = AppInfo(
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        appHostInfo = AppHostInfo(
            displayName = "wifi",
            hostAddress = "192.168.31.1"
        ),
        platform = currentPlatform(),
        state = DeviceState.ONLINE)
    )
    Divider(modifier = Modifier.fillMaxWidth())
    DeviceItem(DeviceInfo(
        deviceId = "abdcs-adasda-sdasdasd",
        appInfo = AppInfo(
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        appHostInfo = AppHostInfo(
            displayName = "wifi",
            hostAddress = "192.168.31.2"
        ),
        platform = object: Platform {
            override val name: String
                get() = "Windows"
            override val bitMode: Int = 64
            override val version: String = "11"
        },
        state = DeviceState.OFFLINE)
    )
    Divider(modifier = Modifier.fillMaxWidth())
    DeviceItem(DeviceInfo(
        deviceId = "abdcs-adasda-sdasdasd",
        appInfo = AppInfo(
            appVersion = "1.0.0",
            userName = "John Doe"
        ),
        appHostInfo = AppHostInfo(
            displayName = "wifi",
            hostAddress = "192.168.31.3"
        ),
        platform = object: Platform {
            override val name: String = "Linux"
            override val bitMode: Int = 32
            override val version: String = "20.23"
        },
        state = DeviceState.UNVERIFIED)
    )
}

@Preview
@Composable
fun test() {
    Column(modifier = Modifier.fillMaxSize()) {
        Devices()
    }
}

