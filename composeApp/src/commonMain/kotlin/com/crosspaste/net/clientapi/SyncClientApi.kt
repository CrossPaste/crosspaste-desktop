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
import io.ktor.utils.io.core.*
import kotlinx.serialization.encodeToString

class SyncClientApi(
    private val pasteClient: PasteClient,
    private val preKeyBundleCodecs: PreKeyBundleCodecs,
    private val signalProtocolStore: SignalProtocolStoreInterface,
) {

    private val logger = KotlinLogging.logger {}

    private val jsonUtils = getJsonUtils()

    suspend fun getPreKeyBundle(toUrl: URLBuilder.() -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "preKeyBundle")
            })
        }) { response ->
            preKeyBundleCodecs.decodePreKeyBundle(response.body<DataContent>().data)
        }
    }

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

    suspend fun createSession(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val data = jsonUtils.JSON.encodeToString(syncInfo).toByteArray()
            val ciphertextMessageBytes = signalMessageProcessor.encrypt(data)
            val dataContent = DataContent(data = ciphertextMessageBytes)
            pasteClient.post(
                dataContent,
                typeInfo<DataContent>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "createSession")
                },
            )
        }, transformData = { true })
    }

    suspend fun heartbeat(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
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
                    toUrl()
                    buildUrl("sync", "heartbeat")
                },
            )
        }, transformData = { true })
    }

    suspend fun isTrust(
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(
                targetAppInstanceId,
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "isTrust")
                },
            )
        }, transformData = { true })
    }

    suspend fun trust(
        token: Int,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val publicKey = signalProtocolStore.getIdentityKeyPublicKey()
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
