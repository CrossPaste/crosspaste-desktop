package com.crosspaste.ui.devices

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Devices
import com.crosspaste.platform.Platform
import com.crosspaste.ui.base.StateTagStyle
import com.crosspaste.ui.base.StateTagView
import com.crosspaste.utils.DeviceUtils
import org.koin.compose.koinInject

private val currentDeviceTagStyle: StateTagStyle
    @Composable @ReadOnlyComposable
    get() =
        StateTagStyle(
            label = "current_device",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = MaterialSymbols.Rounded.Devices,
        )

private class LocalDeviceScope(
    override val platform: Platform,
    private val deviceName: String,
) : PlatformScope {
    override fun getDeviceDisplayName(): String = deviceName
}

/**
 * The local device as the first row of "my devices": same card as a paired
 * device, marked with a "current device" tag after the name. It has no sync
 * state or actions; tapping it opens the device info via [onClick].
 */
@Composable
fun LocalDeviceRow(onClick: () -> Unit) {
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
        nameTrailing = { StateTagView(currentDeviceTagStyle) },
    )
}
