package com.crosspaste.utils

import kotlinx.serialization.json.Json

actual fun getJsonUtils(): JsonUtils = NativeCoreJsonUtils

object NativeCoreJsonUtils : JsonUtils {

    override val JSON: Json = createJSON()
}
