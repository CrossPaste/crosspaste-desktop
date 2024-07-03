package com.crosspaste.utils

import kotlinx.serialization.json.Json

expect fun getJsonUtils(): JsonUtils

interface JsonUtils {

    val JSON: Json
}
