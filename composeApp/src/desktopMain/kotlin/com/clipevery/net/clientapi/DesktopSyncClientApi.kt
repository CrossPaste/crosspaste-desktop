package com.clipevery.net.clientapi

import com.clipevery.dto.sync.ExchangePreKey
import com.clipevery.net.ClipClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.body
import io.ktor.http.URLBuilder
import io.ktor.util.reflect.typeInfo
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle

class DesktopSyncClientApi(private val clipClient: ClipClient): SyncClientApi {

    val logger = KotlinLogging.logger {}

    override suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): PreKeyBundle? {
        try {
            val response = clipClient.get(urlBuilder = toUrl)
            if (response.status.value != 200) {
                return null
            }
            return response.body<PreKeyBundle>()
        } catch (e: Exception) {
            logger.error(e) { "getPreKeyBundle error" }
        }
        return null
    }

    override suspend fun exchangePreKey(sessionCipher: SessionCipher,
                                        toUrl: URLBuilder.(URLBuilder) -> Unit): Boolean {
        try {
            val ciphertextMessage = sessionCipher.encrypt("exchange".toByteArray(Charsets.UTF_8))

            val exchangePreKey = ExchangePreKey(data = ciphertextMessage.serialize())

            val response = clipClient.post(exchangePreKey, typeInfo<ExchangePreKey>() , urlBuilder = toUrl)
            if (response.status.value != 200) {
                return false
            }
            val getExchangePreKey = response.body<ExchangePreKey>()
            val signalMessage = SignalMessage(getExchangePreKey.data)
            val decrypt = sessionCipher.decrypt(signalMessage)
            return String(decrypt, Charsets.UTF_8) == "exchange"
        } catch (e: Exception) {
            logger.error(e) { "exchangePreKey error" }
        }
        return false
    }
}
