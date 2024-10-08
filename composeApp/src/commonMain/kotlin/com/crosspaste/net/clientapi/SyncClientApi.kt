package com.crosspaste.net.clientapi

import com.crosspaste.dto.sync.DataContent
import com.crosspaste.dto.sync.RequestTrust
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.PasteClient
import com.crosspaste.signal.PreKeyBundleCodecs
import com.crosspaste.signal.SignalMessageProcessor
import com.crosspaste.signal.SignalProtocolStoreInterface
import com.crosspaste.utils.buildUrl
import com.crosspaste.utils.getJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.encodeToString

class SyncClientApi(
    private val pasteClient: PasteClient,
    private val preKeyBundleCodecs: PreKeyBundleCodecs,
    private val signalProtocolStore: SignalProtocolStoreInterface,
) {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "preKeyBundle")
            })
        }) { response ->
            preKeyBundleCodecs.decodePreKeyBundle(response.body<DataContent>().data)
        }
    }

    suspend fun syncInfo(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "syncInfo")
            })
        }) { response ->
            response.body<SyncInfo>()
        }
    }

    suspend fun createSession(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val data = jsonUtils.JSON.encodeToString(syncInfo).toByteArray()
            val ciphertextMessageBytes = signalMessageProcessor.encrypt(data)
            val dataContent = DataContent(data = ciphertextMessageBytes)
            pasteClient.post(
                dataContent,
                typeInfo<DataContent>(),
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "sync", "createSession")
                },
            )
        }, transformData = { true })
    }

    suspend fun heartbeat(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val data = jsonUtils.JSON.encodeToString(syncInfo).toByteArray()
            val ciphertextMessageBytes = signalMessageProcessor.encrypt(data)
            val dataContent = DataContent(data = ciphertextMessageBytes)
            pasteClient.post(
                dataContent,
                typeInfo<DataContent>(),
                targetAppInstanceId,
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "sync", "heartbeat")
                },
            )
        }, transformData = { true })
    }

    suspend fun isTrust(
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(
                targetAppInstanceId,
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "sync", "isTrust")
                },
            )
        }, transformData = { true })
    }

    suspend fun trust(
        token: Int,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val publicKey = signalProtocolStore.getIdentityKeyPublicKey()
            val requestTrust = RequestTrust(publicKey, token)
            pasteClient.post(
                requestTrust,
                typeInfo<RequestTrust>(),
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "sync", "trust")
                },
            )
        }, transformData = { true })
    }

    suspend fun showToken(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "showToken")
            })
        }, transformData = { true })
    }

    suspend fun notifyExit(toUrl: URLBuilder.(URLBuilder) -> Unit) {
        request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "notifyExit")
            })
        }, transformData = { true })
    }

    suspend fun notifyRemove(toUrl: URLBuilder.(URLBuilder) -> Unit) {
        request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "notifyRemove")
            })
        }, transformData = { true })
    }
}
