package com.crosspaste.ui

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavType
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.write
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.paste.PasteData
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    val name: String
}

@Serializable
object About : Route {
    const val NAME: String = "about"
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
object Import : Route {
    const val NAME: String = "import"
    override val name: String = NAME
}

@Serializable
data class NearbyDeviceDetail(
    val syncInfo: SyncInfo,
) : Route {
    companion object {
        const val NAME: String = "nearby_device_detail"
    }

    override val name: String = NAME
}

@Serializable
object Pasteboard : Route {
    const val NAME: String = "pasteboard"
    override val name: String = NAME
}

@Serializable
data class PasteTextEdit(
    val pasteData: PasteData,
) : Route {
    companion object {
        const val NAME: String = "text_edit"
    }

    override val name: String = NAME
}

@Serializable
object QrCode : Route {
    const val NAME: String = "scan"
    override val name: String = NAME
}

@Serializable
object Recommend : Route {
    const val NAME: String = "recommend"
    override val name: String = NAME
}

@Serializable
object Settings : Route {
    const val NAME: String = "settings"
    override val name: String = NAME
}

@Serializable
object ShortcutKeys : Route {
    const val NAME: String = "shortcut_keys"
    override val name: String = NAME
}

fun getRouteName(dest: NavDestination): String? =
    when {
        dest.hasRoute<Pasteboard>() -> Pasteboard.NAME
        dest.hasRoute<Devices>() -> Devices.NAME
        dest.hasRoute<DeviceDetail>() -> DeviceDetail.NAME
        dest.hasRoute<Settings>() -> Settings.NAME
        dest.hasRoute<QrCode>() -> QrCode.NAME
        dest.hasRoute<Import>() -> Import.NAME
        dest.hasRoute<Export>() -> Export.NAME
        dest.hasRoute<ShortcutKeys>() -> ShortcutKeys.NAME
        dest.hasRoute<About>() -> About.NAME
        dest.hasRoute<Recommend>() -> Recommend.NAME
        dest.hasRoute<NearbyDeviceDetail>() -> NearbyDeviceDetail.NAME
        dest.hasRoute<PasteTextEdit>() -> PasteTextEdit.NAME
        else -> null
    }

class JsonNavType<T : Any>(
    private val serializer: KSerializer<T>,
    isNullableAllowed: Boolean = false,
) : NavType<T>(isNullableAllowed) {

    val jsonUtils = getJsonUtils()

    override fun parseValue(value: String): T = jsonUtils.JSON.decodeFromString(serializer, value)

    override fun serializeAsValue(value: T): String = jsonUtils.JSON.encodeToString(serializer, value)

    override fun get(
        bundle: SavedState,
        key: String,
    ): T {
        val s: String = bundle.read { getString(key) }
        return parseValue(s)
    }

    override fun put(
        bundle: SavedState,
        key: String,
        value: T,
    ) {
        val s = serializeAsValue(value)
        bundle.write { putString(key, s) }
    }
}
