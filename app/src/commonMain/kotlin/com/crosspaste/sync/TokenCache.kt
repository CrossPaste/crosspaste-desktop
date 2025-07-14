package com.crosspaste.sync

import io.ktor.util.collections.*

object TokenCache {
    private val tokenCache: MutableMap<String, Int> = ConcurrentMap()

    fun setToken(
        appInstanceId: String,
        token: Int,
    ) {
        tokenCache[appInstanceId] = token
    }

    fun getToken(appInstanceId: String): Int? = tokenCache.remove(appInstanceId)
}
