package com.crosspaste.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.crosspaste.i18n.GlobalCopywriter
import com.crosspaste.platform.Platform
import com.crosspaste.utils.DeviceUtils
import org.koin.compose.koinInject

private class LocalDeviceScope(
    override val platform: Platform,
    private val deviceName: String,
) : PlatformScope {
    override fun getDeviceDisplayName(): String = deviceName
}

/**
 * The local device as the first row of "my devices": same card as a paired
 * device, marked with a plain "this device" caption after the name (see
 * [DeviceNameMarker]). It has no sync state or actions; tapping it opens the
 * device info via [onClick].
 */
@Composable
fun LocalDeviceRow(onClick: () -> Unit) {
    val copywriter = koinInject<GlobalCopywriter>()
    val platform = koinInject<Platform>()
    val deviceUtils = koinInject<DeviceUtils>()

    val scope =
        remember(platform, deviceUtils) {
            LocalDeviceScope(platform, deviceUtils.getDeviceName())
        }

    scope.DeviceRowContent(
        style = myDeviceStyle,
        onClick = onClick,
        iconTint = MaterialTheme.colorScheme.primary,
        nameTrailing = { DeviceNameMarker(copywriter.getText("this_device")) },
    )
}
