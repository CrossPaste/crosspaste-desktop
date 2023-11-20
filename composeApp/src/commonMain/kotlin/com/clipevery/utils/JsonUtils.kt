package com.clipevery.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

inline fun <reified T> readJson(json: String): T {
    return Json.decodeFromString<T>(json)
}

inline fun <reified T : Any> writeJson(obj: T): String {
    return Json.encodeToString(obj)
}