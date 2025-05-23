package com.crosspaste.ui

interface ScreenType {
    val name: String
}

object About : ScreenType {
    override val name: String = "About"
}

object Export : ScreenType {
    override val name: String = "Export"
}

object Debug : ScreenType {
    override val name: String = "Debug"
}

object Devices : ScreenType {
    override val name: String = "Devices"
}

object DeviceDetail : ScreenType {
    override val name: String = "DeviceDetail"
}

object Import : ScreenType {
    override val name: String = "Import"
}

object PastePreview : ScreenType {
    override val name: String = "PastePreview"
}

object PasteTextEdit : ScreenType {
    override val name: String = "PasteTextEdit"
}

object QrCode : ScreenType {
    override val name: String = "QrCode"
}

object Recommend : ScreenType {
    override val name: String = "recommend"
}

object Settings : ScreenType {
    override val name: String = "Settings"
}

object ShortcutKeys : ScreenType {
    override val name: String = "ShortcutKeys"
}
