package com.crosspaste.utils

import kotlinx.serialization.json.Json

actual fun getJsonUtils(): JsonUtils = DesktopCoreJsonUtils

object DesktopCoreJsonUtils : JsonUtils {

    override val JSON: Json = createJSON()
}
