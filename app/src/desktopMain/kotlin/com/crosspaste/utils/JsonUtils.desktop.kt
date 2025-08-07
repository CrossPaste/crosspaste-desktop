package com.crosspaste.utils

import kotlinx.serialization.json.Json

actual fun getJsonUtils(): JsonUtils = DesktopJsonUtils

object DesktopJsonUtils : JsonUtils {

    override val JSON: Json = createJSON()
}
