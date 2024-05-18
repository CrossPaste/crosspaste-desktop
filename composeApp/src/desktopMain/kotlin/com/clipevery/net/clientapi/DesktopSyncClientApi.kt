package com.clipevery.net.clientapi

import com.clipevery.dto.sync.DataContent
import com.clipevery.dto.sync.RequestTrust
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.net.ClipClient
import com.clipevery.serializer.PreKeyBundleSerializer
import com.clipevery.utils.DesktopJsonUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlinx.serialization.encodeToString
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.SignalProtocolStore

class DesktopSyncClientApi(
    private val clipClient: ClipClient,
    private val signalProtocolStore: SignalProtocolStore,
) : SyncClientApi {

    private val logger = KotlinLogging.logger {}

    override suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): PreKeyBundle? {
        try {
            val response = clipClient.get(urlBuilder = toUrl)
            if (response.status.value != 200) {
                return null
            }
            return PreKeyBundleSerializer.decodePreKeyBundle(response.body<DataContent>().data)
        } catch (e: Exception) {
            logger.error(e) { "getPreKeyBundle error" }
        }
        return null
    }

    override suspend fun exchangeSyncInfo(
        syncInfo: SyncInfo,
        sessionCipher: SessionCipher,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): Boolean {
        try {
            val data = DesktopJsonUtils.JSON.encodeToString(syncInfo).toByteArray()
            val ciphertextMessage = sessionCipher.encrypt(data)

            val dataContent = DataContent(data = ciphertextMessage.serialize())

            val response = clipClient.post(dataContent, typeInfo<DataContent>(), urlBuilder = toUrl)
            return response.status.value == 200
        } catch (e: Exception) {
            logger.error(e) { "exchangeSyncInfo error" }
        }
        return false
    }

    override suspend fun isTrust(toUrl: URLBuilder.(URLBuilder) -> Unit): Boolean {
        try {
            val response = clipClient.get(urlBuilder = toUrl)
            return response.status.value == 200
        } catch (e: Exception) {
            logger.error(e) { "isTrust api fail" }
            return false
        }
    }

    override suspend fun trust(
        token: Int,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): Boolean {
        try {
            val identityKey = signalProtocolStore.identityKeyPair.publicKey
            val requestTrust = RequestTrust(identityKey, token)
            val response = clipClient.post(requestTrust, typeInfo<RequestTrust>(), urlBuilder = toUrl)
            return response.status.value == 200
        } catch (e: Exception) {
            logger.error(e) { "trust api fail" }
            return false
        }
    }

    override suspend fun showToken(toUrl: URLBuilder.(URLBuilder) -> Unit): Boolean {
        try {
            clipClient.get(urlBuilder = toUrl)
            return true
        } catch (e: Exception) {
            logger.error(e) { "showToken api fail" }
            return false
        }
    }
}
