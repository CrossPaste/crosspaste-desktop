package com.crosspaste.net.clientapi

import com.crosspaste.dto.secure.PairingRequest
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
                    append("host", host)
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
