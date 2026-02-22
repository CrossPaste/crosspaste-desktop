package com.crosspaste.ui

import kotlinx.serialization.Serializable

interface Route {
    val name: String
}

@Serializable
object About : Route {
    const val NAME: String = "about"
    override val name: String = NAME
}

@Serializable
object ExtensionGraph : Route {
    override val name = "extension_graph"
}

@Serializable
object Extension : Route {
    const val NAME: String = "extension"
    override val name: String = NAME
}

@Serializable
object Export : Route {
    const val NAME: String = "export"
    override val name: String = NAME
}

@Serializable
object Devices : Route {
    const val NAME: String = "devices"
    override val name: String = NAME
}

@Serializable
data class DeviceDetail(
    val appInstanceId: String,
) : Route {
    companion object {
        const val NAME: String = "device_detail"
    }

    override val name: String = NAME
}

@Serializable
object DevicesGraph : Route {
    override val name = "devices_graph"
}

@Serializable
object Import : Route {
    const val NAME: String = "import"
    override val name: String = NAME
}

@Serializable
data class NearbyDeviceDetail(
    val appInstanceId: String,
) : Route {
    companion object {
        const val NAME: String = "nearby_device_detail"
    }

    override val name: String = NAME
}

@Serializable
object MCP : Route {
    const val NAME: String = "mcp_settings"
    override val name: String = NAME
}

@Serializable
object OCR : Route {
    const val NAME: String = "ocr_settings"
    override val name: String = NAME
}

@Serializable
object SourceControl : Route {
    const val NAME: String = "source_control_settings"
    override val name: String = NAME
}

@Serializable
object Pasteboard : Route {
    const val NAME: String = "pasteboard"
    override val name: String = NAME
}

@Serializable
object PairingCode : Route {
    const val NAME: String = "pairing_code"
    override val name: String = NAME
}

@Serializable
object Share : Route {
    const val NAME: String = "share"
    override val name: String = NAME
}

@Serializable
object SettingsGraph : Route {
    override val name = "settings_graph"
}

@Serializable
object Settings : Route {
    const val NAME: String = "settings"
    override val name: String = NAME
}

@Serializable
object PasteboardSettings : Route {
    const val NAME: String = "pasteboard_settings"
    override val name: String = NAME
}

@Serializable
object NetworkSettings : Route {
    const val NAME: String = "network_settings"
    override val name: String = NAME
}

@Serializable
object StorageSettings : Route {
    const val NAME: String = "storage_settings"
    override val name: String = NAME
}

@Serializable
object ShortcutKeys : Route {
    const val NAME: String = "shortcut_keys"
    override val name: String = NAME
}
