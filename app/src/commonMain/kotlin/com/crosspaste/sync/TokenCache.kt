package com.crosspaste.sync

import io.ktor.util.collections.*

interface TokenCacheApi {

    fun setToken(
        appInstanceId: String,
        token: QrBearerToken,
    )

    fun getToken(appInstanceId: String): QrBearerToken?
}

object TokenCache : TokenCacheApi {
    private val tokenCache: MutableMap<String, QrBearerToken> = ConcurrentMap()

    override fun setToken(
        appInstanceId: String,
        token: QrBearerToken,
    ) {
        tokenCache[appInstanceId] = token
    }

    override fun getToken(appInstanceId: String): QrBearerToken? = tokenCache.remove(appInstanceId)
}
