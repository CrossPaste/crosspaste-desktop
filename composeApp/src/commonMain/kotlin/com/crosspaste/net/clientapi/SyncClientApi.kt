package com.crosspaste.net.clientapi

import com.crosspaste.dto.sync.RequestTrust
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.PasteClient
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*

class SyncClientApi(
    private val pasteClient: PasteClient,
    private val secureStore: SecureStore,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun syncInfo(toUrl: URLBuilder.() -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "syncInfo")
            })
        }) { response ->
            response.body<SyncInfo>()
        }
    }

    suspend fun heartbeat(
        syncInfo: SyncInfo,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            pasteClient.post(
                syncInfo,
                typeInfo<SyncInfo>(),
                targetAppInstanceId,
                encrypt = true,
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "heartbeat")
                },
            )
        }, transformData = { true })
    }

    suspend fun trust(
        token: Int,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val publicKey = secureStore.getPublicKey()
            val requestTrust = RequestTrust(publicKey, token)
            pasteClient.post(
                requestTrust,
                typeInfo<RequestTrust>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "trust")
                },
            )
        }, transformData = { true })
    }

    suspend fun showToken(toUrl: URLBuilder.() -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "showToken")
            })
        }, transformData = { true })
    }

    suspend fun notifyExit(toUrl: URLBuilder.() -> Unit) {
        request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "notifyExit")
            })
        }, transformData = { true })
    }

    suspend fun notifyRemove(toUrl: URLBuilder.() -> Unit) {
        request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "notifyRemove")
            })
        }, transformData = { true })
    }
}
