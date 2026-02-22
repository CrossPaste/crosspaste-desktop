package com.crosspaste.ui

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

fun getRouteName(dest: NavDestination): String? =
    when {
        dest.hasRoute<About>() -> About.NAME
        dest.hasRoute<DeviceDetail>() -> DeviceDetail.NAME
        dest.hasRoute<Devices>() -> Devices.NAME
        dest.hasRoute<Export>() -> Export.NAME
        dest.hasRoute<Extension>() -> Extension.NAME
        dest.hasRoute<Import>() -> Import.NAME
        dest.hasRoute<NearbyDeviceDetail>() -> NearbyDeviceDetail.NAME
        dest.hasRoute<OCR>() -> OCR.NAME
        dest.hasRoute<MCP>() -> MCP.NAME
        dest.hasRoute<SourceControl>() -> SourceControl.NAME
        dest.hasRoute<Pasteboard>() -> Pasteboard.NAME
        dest.hasRoute<PairingCode>() -> PairingCode.NAME
        dest.hasRoute<Share>() -> Share.NAME
        dest.hasRoute<Settings>() -> Settings.NAME
        dest.hasRoute<PasteboardSettings>() -> PasteboardSettings.NAME
        dest.hasRoute<NetworkSettings>() -> NetworkSettings.NAME
        dest.hasRoute<StorageSettings>() -> StorageSettings.NAME
        dest.hasRoute<ShortcutKeys>() -> ShortcutKeys.NAME
        else -> null
    }

fun getRootRouteName(dest: NavDestination): String? =
    when {
        dest.hasRoute<About>() -> About.NAME
        dest.hasRoute<DeviceDetail>() -> Devices.NAME
        dest.hasRoute<Devices>() -> Devices.NAME
        dest.hasRoute<Export>() -> Export.NAME
        dest.hasRoute<Extension>() -> Extension.NAME
        dest.hasRoute<Import>() -> Import.NAME
        dest.hasRoute<NearbyDeviceDetail>() -> Devices.NAME
        dest.hasRoute<OCR>() -> Extension.NAME
        dest.hasRoute<MCP>() -> Extension.NAME
        dest.hasRoute<SourceControl>() -> Extension.NAME
        dest.hasRoute<Pasteboard>() -> Pasteboard.NAME
        dest.hasRoute<PairingCode>() -> PairingCode.NAME
        dest.hasRoute<Share>() -> Share.NAME
        dest.hasRoute<Settings>() -> Settings.NAME
        dest.hasRoute<PasteboardSettings>() -> Settings.NAME
        dest.hasRoute<NetworkSettings>() -> Settings.NAME
        dest.hasRoute<StorageSettings>() -> Settings.NAME
        dest.hasRoute<ShortcutKeys>() -> ShortcutKeys.NAME
        else -> null
    }
