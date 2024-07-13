package com.crosspaste.net.clientapi

import com.crosspaste.dto.sync.DataContent
import com.crosspaste.dto.sync.RequestTrust
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.PasteClient
import com.crosspaste.serializer.PreKeyBundleSerializer
import com.crosspaste.signal.SignalMessageProcessor
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlinx.serialization.encodeToString
import org.signal.libsignal.protocol.state.SignalProtocolStore

class DesktopSyncClientApi(
    private val pasteClient: PasteClient,
    private val signalProtocolStore: SignalProtocolStore,
) : SyncClientApi {

    private val logger = KotlinLogging.logger {}

    override suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "preKeyBundle")
            })
        }) { response ->
            PreKeyBundleSerializer.decodePreKeyBundle(response.body<DataContent>().data)
        }
    }

    override suspend fun createSession(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val data = DesktopJsonUtils.JSON.encodeToString(syncInfo).toByteArray()
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

    override suspend fun heartbeat(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val data = DesktopJsonUtils.JSON.encodeToString(syncInfo).toByteArray()
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

    override suspend fun isTrust(
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

    override suspend fun trust(
        token: Int,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val identityKey = signalProtocolStore.identityKeyPair.publicKey
            val requestTrust = RequestTrust(identityKey.serialize(), token)
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

    override suspend fun showToken(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult {
        return request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "showToken")
            })
        }, transformData = { true })
    }

    override suspend fun notifyExit(toUrl: URLBuilder.(URLBuilder) -> Unit) {
        request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "notifyExit")
            })
        }, transformData = { true })
    }

    override suspend fun notifyRemove(toUrl: URLBuilder.(URLBuilder) -> Unit) {
        request(logger, request = {
            pasteClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "notifyRemove")
            })
        }, transformData = { true })
    }
}
