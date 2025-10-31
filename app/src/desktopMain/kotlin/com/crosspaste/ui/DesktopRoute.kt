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
        dest.hasRoute<Pasteboard>() -> Pasteboard.NAME
        dest.hasRoute<PasteTextEdit>() -> PasteTextEdit.NAME
        dest.hasRoute<QrCode>() -> QrCode.NAME
        dest.hasRoute<Recommend>() -> Recommend.NAME
        dest.hasRoute<Settings>() -> Settings.NAME
        dest.hasRoute<ShortcutKeys>() -> ShortcutKeys.NAME
        else -> null
    }
