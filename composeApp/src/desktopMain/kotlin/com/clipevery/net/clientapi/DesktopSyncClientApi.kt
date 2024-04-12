package com.clipevery.net.clientapi

import com.clipevery.dto.sync.DataContent
import com.clipevery.dto.sync.RequestTrust
import com.clipevery.net.ClipClient
import com.clipevery.utils.decodePreKeyBundle
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.message.SignalMessage
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
            return decodePreKeyBundle(response.body<DataContent>().data)
        } catch (e: Exception) {
            logger.error(e) { "getPreKeyBundle error" }
        }
        return null
    }

    override suspend fun exchangePreKey(
        sessionCipher: SessionCipher,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): Boolean {
        try {
            val ciphertextMessage = sessionCipher.encrypt("exchange".toByteArray(Charsets.UTF_8))

            val dataContent = DataContent(data = ciphertextMessage.serialize())

            val response = clipClient.post(dataContent, typeInfo<DataContent>(), urlBuilder = toUrl)
            if (response.status.value != 200) {
                return false
            }
            val getDataContent = response.body<DataContent>()
            val signalMessage = SignalMessage(getDataContent.data)
            val decrypt = sessionCipher.decrypt(signalMessage)
            return String(decrypt, Charsets.UTF_8) == "exchange"
        } catch (e: Exception) {
            logger.error(e) { "exchangePreKey error" }
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

    override suspend fun showToken(toUrl: URLBuilder.(URLBuilder) -> Unit) {
        clipClient.get(urlBuilder = toUrl)
    }
}
