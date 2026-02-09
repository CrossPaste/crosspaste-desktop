package com.crosspaste.sync

import io.ktor.util.collections.*

interface TokenCacheApi {

    fun setToken(
        appInstanceId: String,
        token: Int,
    )

    fun getToken(appInstanceId: String): Int?
}

object TokenCache : TokenCacheApi {
    private val tokenCache: MutableMap<String, Int> = ConcurrentMap()

    override fun setToken(
        appInstanceId: String,
        token: Int,
    ) {
        tokenCache[appInstanceId] = token
    }

    override fun getToken(appInstanceId: String): Int? = tokenCache.remove(appInstanceId)
}
