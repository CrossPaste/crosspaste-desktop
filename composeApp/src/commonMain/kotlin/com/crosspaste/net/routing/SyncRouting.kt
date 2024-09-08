package com.crosspaste.net.routing

import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenService
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.dto.sync.DataContent
import com.crosspaste.dto.sync.RequestTrust
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.signal.PreKeyBundleCodecs
import com.crosspaste.signal.PreKeySignalMessageFactory
import com.crosspaste.signal.SignalAddress
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.signal.SignalProtocolStoreInterface
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.getJsonUtils
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

fun Routing.syncRouting(
    appInfo: AppInfo,
    appTokenService: AppTokenService,
    preKeyBundleCodecs: PreKeyBundleCodecs,
    preKeySignalMessageFactory: PreKeySignalMessageFactory,
    signalDao: SignalDao,
    signalProtocolStore: SignalProtocolStoreInterface,
    signalProcessorCache: SignalProcessorCache,
    syncManager: SyncManager,
) {
    val logger = KotlinLogging.logger {}

    val jsonUtils = getJsonUtils()

    get("/sync/preKeyBundle") {
        getAppInstanceId(call)?.let { appInstanceId ->

            val signalAddress = SignalAddress(appInstanceId, 1)
            val existIdentityKey = signalProtocolStore.existIdentity(signalAddress)

            if (!existIdentityKey) {
                logger.debug { "${appInfo.appInstanceId} not trust $appInstanceId in /sync/preKeyBundle api" }
                failResponse(call, StandardErrorCode.SIGNAL_UNTRUSTED_IDENTITY.toErrorCode(), "not trust $appInstanceId")
                return@get
            }

            val preKeyBundle = signalProtocolStore.generatePreKeyBundle(signalDao)

            val bytes = preKeyBundleCodecs.encodePreKeyBundle(preKeyBundle)
            logger.debug { "${appInfo.appInstanceId} create preKeyBundle for $appInstanceId:\n $preKeyBundle" }
            successResponse(call, DataContent(bytes))
        }
    }

    post("/sync/createSession") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val dataContent = call.receive(DataContent::class)
            val bytes = dataContent.data
            signalProcessorCache.removeSignalMessageProcessor(appInstanceId)
            val processor = signalProcessorCache.getSignalMessageProcessor(appInstanceId)
            val preKeySignalMessage = preKeySignalMessageFactory.createPreKeySignalMessage(bytes)

            val signedPreKeyId = preKeySignalMessage.getSignedPreKeyId()

            if (signalProtocolStore.containsSignedPreKey(signedPreKeyId)) {
                signalProtocolStore.saveIdentity(
                    processor.getSignalAddress(),
                    preKeySignalMessage,
                )
                val decrypt = processor.decryptPreKeySignalMessage(preKeySignalMessage)

                try {
                    val syncInfo = jsonUtils.JSON.decodeFromString<SyncInfo>(String(decrypt, Charsets.UTF_8))
                    syncManager.updateSyncInfo(syncInfo)
                    logger.info { "$appInstanceId createSession to ${appInfo.appInstanceId} success" }
                    successResponse(call)
                } catch (e: Exception) {
                    logger.error(e) { "$appInstanceId createSession to ${appInfo.appInstanceId} fail" }
                    failResponse(call, StandardErrorCode.SIGNAL_EXCHANGE_FAIL.toErrorCode())
                }
            } else {
                logger.error { "$appInstanceId createSession to ${appInfo.appInstanceId}, not contain signedPreKeyId" }
                failResponse(call, StandardErrorCode.SIGNAL_INVALID_KEY_ID.toErrorCode())
                return@let
            }
        }
    }

    get("/sync/showToken") {
        appTokenService.toShowToken()
        logger.debug { "show token" }
        successResponse(call)
    }

    get("/sync/isTrust") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.debug { "isTrust targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
                return@let
            }
            val signalAddress = SignalAddress(appInstanceId, 1)
            if (signalProtocolStore.existIdentity(signalAddress)) {
                logger.debug { "${appInfo.appInstanceId} isTrust $appInstanceId" }
                successResponse(call)
            } else {
                logger.debug { "${appInfo.appInstanceId} not trust $appInstanceId in /sync/isTrust api" }
                failResponse(call, StandardErrorCode.SIGNAL_UNTRUSTED_IDENTITY.toErrorCode(), "not trust $appInstanceId")
            }
        }
    }

    post("/sync/trust") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val requestTrust = call.receive(RequestTrust::class)
            if (requestTrust.token == String(appTokenService.token).toInt()) {
                val signalAddress = SignalAddress(appInstanceId, 1)
                signalProtocolStore.saveIdentity(
                    signalAddress,
                    requestTrust,
                )
                appTokenService.showToken = false
                logger.debug { "${appInfo.appInstanceId} to trust $appInstanceId" }
                successResponse(call)
            } else {
                logger.error { "token invalid: ${requestTrust.token}" }
                failResponse(call, StandardErrorCode.TOKEN_INVALID.toErrorCode())
            }
        }
    }
}
