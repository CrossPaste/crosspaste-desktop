package com.crosspaste.ui

interface ScreenType {
    val name: String
}

object About : ScreenType {
    override val name: String = "about"
}

object Export : ScreenType {
    override val name: String = "export"
}

object Debug : ScreenType {
    override val name: String = "debug"
}

object Devices : ScreenType {
    override val name: String = "devices"
}

object DeviceDetail : ScreenType {
    override val name: String = "device_detail"
}

object Import : ScreenType {
    override val name: String = "import"
}

object NearbyDeviceDetail : ScreenType {
    override val name: String = "nearby_device_detail"
}

object Pasteboard : ScreenType {
    override val name: String = "pasteboard"
}

object PasteTextEdit : ScreenType {
    override val name: String = "text_edit"
}

object QrCode : ScreenType {
    override val name: String = "scan"
}

object Recommend : ScreenType {
    override val name: String = "recommend"
}

object Settings : ScreenType {
    override val name: String = "settings"
}

object ShortcutKeys : ScreenType {
    override val name: String = "shortcut_keys"
}
