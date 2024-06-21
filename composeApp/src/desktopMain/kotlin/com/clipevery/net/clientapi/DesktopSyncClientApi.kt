package com.clipevery.net.clientapi

import com.clipevery.dto.sync.DataContent
import com.clipevery.dto.sync.RequestTrust
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.net.ClipClient
import com.clipevery.serializer.PreKeyBundleSerializer
import com.clipevery.signal.SignalMessageProcessor
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlinx.serialization.encodeToString
import org.signal.libsignal.protocol.state.SignalProtocolStore

class DesktopSyncClientApi(
    private val clipClient: ClipClient,
    private val signalProtocolStore: SignalProtocolStore,
) : SyncClientApi {

    private val logger = KotlinLogging.logger {}

    override suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult {
        return request(logger, request = {
            clipClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "preKeyBundle")
            })
        }) { response ->
            PreKeyBundleSerializer.decodePreKeyBundle(response.body<DataContent>().data)
        }
    }

    override suspend fun exchangeSyncInfo(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val data = DesktopJsonUtils.JSON.encodeToString(syncInfo).toByteArray()
            val ciphertextMessage = signalMessageProcessor.encrypt(data)
            val dataContent = DataContent(data = ciphertextMessage.serialize())
            clipClient.post(
                dataContent,
                typeInfo<DataContent>(),
                urlBuilder = {
                    toUrl(it)
                    buildUrl(it, "sync", "exchangeSyncInfo")
                },
            )
        }, transformData = { true })
    }

    override suspend fun isTrust(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult {
        return request(logger, request = {
            clipClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "isTrust")
            })
        }, transformData = { true })
    }

    override suspend fun trust(
        token: Int,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val identityKey = signalProtocolStore.identityKeyPair.publicKey
            val requestTrust = RequestTrust(identityKey, token)
            clipClient.post(
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
            clipClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "showToken")
            })
        }, transformData = { true })
    }

    override suspend fun notifyExit(toUrl: URLBuilder.(URLBuilder) -> Unit) {
        request(logger, request = {
            clipClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "notifyExit")
            })
        }, transformData = { true })
    }

    override suspend fun notifyRemove(toUrl: URLBuilder.(URLBuilder) -> Unit) {
        request(logger, request = {
            clipClient.get(urlBuilder = {
                toUrl(it)
                buildUrl(it, "sync", "notifyRemove")
            })
        }, transformData = { true })
    }
}
