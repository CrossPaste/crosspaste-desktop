package com.crosspaste.net.clientapi

import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.PasteClient
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.util.reflect.*
import kotlinx.datetime.Clock

class SyncClientApi(
    private val pasteClient: PasteClient,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
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
        targetAppInstanceId: String,
        token: Int,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult {
        return request(logger, request = {
            val signPublicKey = secureStore.getSignPublicKeyBytes()
            val cryptPublicKey = secureStore.getCryptPublicKeyBytes()
            val pairingRequest = PairingRequest(
                signPublicKey,
                cryptPublicKey,
                token,
                Clock.System.now().toEpochMilliseconds(),
            )
            val sign = CryptographyUtils.signPairingRequest(
                secureStore.getSecureKeyPair().signKeyPair.privateKey,
                pairingRequest,
            )
            val trustRequest = TrustRequest(
                pairingRequest,
                sign,
            )
            pasteClient.post(
                trustRequest,
                typeInfo<TrustRequest>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "trust")
                },
            )
        }, transformData = {
            val trustResponse = it.body<TrustResponse>()
            val receiveSignPublicKey = secureKeyPairSerializer.decodeSignPublicKey(
                trustResponse.pairingResponse.signPublicKey,
            )
            val verifyResult = CryptographyUtils.verifyPairingResponse(
                receiveSignPublicKey,
                trustResponse.pairingResponse,
                trustResponse.signature,
            )

            if (verifyResult) {
                secureStore.saveCryptPublicKey(targetAppInstanceId, trustResponse.pairingResponse.cryptPublicKey)
                true
            } else {
                false
            }
        })
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
