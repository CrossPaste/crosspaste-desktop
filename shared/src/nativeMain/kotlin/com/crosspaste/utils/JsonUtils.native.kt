package com.crosspaste.utils

import kotlinx.serialization.json.Json

actual fun getJsonUtils(): JsonUtils = NativeJsonUtils

object NativeJsonUtils : JsonUtils {

    override val JSON: Json = createJSON()
}
