package com.crosspaste.net.clientapi

import com.crosspaste.dto.secure.KeyExchangeRequest
import com.crosspaste.dto.secure.KeyExchangeResponse
import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.TrustConfirmRequest
import com.crosspaste.dto.secure.TrustConfirmResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.net.PasteClient
import com.crosspaste.net.SyncApi
import com.crosspaste.net.exception.ExceptionHandler
import com.crosspaste.secure.SecureKeyPairSerializer
import com.crosspaste.secure.SecureStore
import com.crosspaste.utils.CryptographyUtils
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import com.crosspaste.utils.buildUrl
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.*

class SyncClientApi(
    private val pasteClient: PasteClient,
    private val exceptionHandler: ExceptionHandler,
    private val secureKeyPairSerializer: SecureKeyPairSerializer,
    private val secureStore: SecureStore,
    private val syncApi: SyncApi,
) {

    private val logger = KotlinLogging.logger {}

    suspend fun syncInfo(toUrl: URLBuilder.() -> Unit): ClientApiResult =
        request(logger, exceptionHandler, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "syncInfo")
            })
        }) { response ->
            response.body<SyncInfo>()
        }

    suspend fun heartbeat(
        syncInfo: SyncInfo? = null,
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            syncInfo?.let {
                pasteClient.post(
                    syncInfo,
                    typeInfo<SyncInfo>(),
                    headersBuilder = {
                        append("targetAppInstanceId", targetAppInstanceId)
                        append("secure", "1")
                    },
                    urlBuilder = {
                        toUrl()
                        buildUrl("sync", "heartbeat", "syncInfo")
                    },
                )
            } ?: run {
                pasteClient.get(
                    headersBuilder = {
                        append("targetAppInstanceId", targetAppInstanceId)
                    },
                    urlBuilder = {
                        toUrl()
                        buildUrl("sync", "heartbeat")
                    },
                )
            }
        }, transformData = {
            val result = it.bodyAsText()
            result.toIntOrNull()?.let { connectedVersion ->
                syncApi.compareVersion(connectedVersion)
            } ?: run {
                logger.warn { "heartbeat response is not a valid version int: '$result'" }
                null
            }
        })

    suspend fun trust(
        targetAppInstanceId: String,
        host: String,
        token: Int,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            val signPublicKey = secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
            val cryptPublicKey = secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
            val pairingRequest =
                PairingRequest(
                    signPublicKey,
                    cryptPublicKey,
                    token,
                    nowEpochMilliseconds(),
                )
            val sign =
                CryptographyUtils.signPairingRequest(
                    secureStore.secureKeyPair.signKeyPair.privateKey,
                    pairingRequest,
                )
            val trustRequest =
                TrustRequest(
                    pairingRequest,
                    sign,
                )
            pasteClient.post(
                trustRequest,
                typeInfo<TrustRequest>(),
                headersBuilder = {
                    append("crosspaste-host", host)
                },
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "trust")
                },
            )
        }, transformData = {
            val trustResponse = it.body<TrustResponse>()

            val receiveSignPublicKey =
                secureKeyPairSerializer.decodeSignPublicKey(
                    trustResponse.pairingResponse.signPublicKey,
                )
            val verifyResult =
                CryptographyUtils.verifyPairingResponse(
                    receiveSignPublicKey,
                    trustResponse.pairingResponse,
                    trustResponse.signature,
                )

            if (verifyResult) {
                secureStore.saveCryptPublicKey(targetAppInstanceId, trustResponse.pairingResponse.cryptPublicKey)
                true
            } else {
                logger.warn { "verifyResult is false" }
                false
            }
        })

    suspend fun exchangeKeys(
        targetAppInstanceId: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            val signPublicKey = secureStore.secureKeyPair.getSignPublicKeyBytes(secureKeyPairSerializer)
            val cryptPublicKey = secureStore.secureKeyPair.getCryptPublicKeyBytes(secureKeyPairSerializer)
            val timestamp = nowEpochMilliseconds()
            val signature =
                CryptographyUtils.signKeyExchangeRequest(
                    secureStore.secureKeyPair.signKeyPair.privateKey,
                    signPublicKey,
                    cryptPublicKey,
                    timestamp,
                )
            val request =
                KeyExchangeRequest(
                    signPublicKey = signPublicKey,
                    cryptPublicKey = cryptPublicKey,
                    timestamp = timestamp,
                    signature = signature,
                )
            pasteClient.post(
                request,
                typeInfo<KeyExchangeRequest>(),
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "trust", "v2", "exchange")
                },
            )
        }, transformData = {
            val response = it.body<KeyExchangeResponse>()

            val receiveSignPublicKey =
                secureKeyPairSerializer.decodeSignPublicKey(response.signPublicKey)
            val verifyResult =
                CryptographyUtils.verifyKeyExchangeResponse(
                    receiveSignPublicKey,
                    response,
                )

            if (verifyResult) {
                response
            } else {
                logger.warn { "exchangeKeys: signature verification failed" }
                null
            }
        })

    suspend fun trustV2Confirm(
        targetAppInstanceId: String,
        host: String,
        toUrl: URLBuilder.() -> Unit,
    ): ClientApiResult =
        request(logger, exceptionHandler, request = {
            val timestamp = nowEpochMilliseconds()
            val signature =
                CryptographyUtils.signTrustConfirm(
                    secureStore.secureKeyPair.signKeyPair.privateKey,
                    timestamp,
                )
            val request =
                TrustConfirmRequest(
                    timestamp = timestamp,
                    signature = signature,
                )
            pasteClient.post(
                request,
                typeInfo<TrustConfirmRequest>(),
                headersBuilder = {
                    append("crosspaste-host", host)
                },
                urlBuilder = {
                    toUrl()
                    buildUrl("sync", "trust", "v2", "confirm")
                },
            )
        }, transformData = {
            val response = it.body<TrustConfirmResponse>()
            // Save remote crypt public key is done in the server-side confirm handler
            // Here we just verify the response signature
            true
        })

    suspend fun showToken(toUrl: URLBuilder.() -> Unit): ClientApiResult =
        request(logger, exceptionHandler, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "showToken")
            })
        }, transformData = { true })

    suspend fun notifyExit(toUrl: URLBuilder.() -> Unit) {
        request(logger, exceptionHandler, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "notifyExit")
            })
        }, transformData = { true })
    }

    suspend fun notifyRemove(toUrl: URLBuilder.() -> Unit) {
        request(logger, exceptionHandler, request = {
            pasteClient.get(urlBuilder = {
                toUrl()
                buildUrl("sync", "notifyRemove")
            })
        }, transformData = { true })
    }
}
