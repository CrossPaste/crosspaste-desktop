package com.crosspaste.utils

import kotlinx.serialization.json.Json

actual fun getJsonUtils(): JsonUtils = JsJsonUtils

object JsJsonUtils : JsonUtils {

    override val JSON: Json = createJSON()
}
