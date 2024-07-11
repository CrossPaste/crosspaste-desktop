package com.crosspaste.routing

import com.crosspaste.CrossPaste
import com.crosspaste.app.AppInfo
import com.crosspaste.app.AppTokenService
import com.crosspaste.app.AppWindowManager
import com.crosspaste.dao.signal.SignalDao
import com.crosspaste.dto.sync.DataContent
import com.crosspaste.dto.sync.RequestTrust
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.serializer.PreKeyBundleSerializer
import com.crosspaste.signal.SignalProcessorCache
import com.crosspaste.sync.SyncManager
import com.crosspaste.utils.DesktopJsonUtils
import com.crosspaste.utils.EncryptUtils
import com.crosspaste.utils.failResponse
import com.crosspaste.utils.getAppInstanceId
import com.crosspaste.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

fun Routing.syncRouting() {
    val logger = KotlinLogging.logger {}

    val koinApplication = CrossPaste.koinApplication

    val appInfo = koinApplication.koin.get<AppInfo>()

    val signalDao = koinApplication.koin.get<SignalDao>()

    val signalProtocolStore = koinApplication.koin.get<SignalProtocolStore>()

    val signalProcessorCache = koinApplication.koin.get<SignalProcessorCache>()

    val syncManager = koinApplication.koin.get<SyncManager>()

    get("/sync/telnet") {
        successResponse(call)
    }

    get("/sync/preKeyBundle") {
        getAppInstanceId(call)?.let { appInstanceId ->

            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)

            signalProtocolStore.getIdentity(signalProtocolAddress) ?: run {
                logger.debug { "${appInfo.appInstanceId} not trust $appInstanceId in /sync/preKeyBundle api" }
                failResponse(call, StandardErrorCode.SIGNAL_UNTRUSTED_IDENTITY.toErrorCode(), "not trust $appInstanceId")
                return@get
            }

            val identityKeyPair = signalProtocolStore.identityKeyPair
            val registrationId = signalProtocolStore.localRegistrationId
            val deviceId = 1
            val preKey = EncryptUtils.generatePreKeyPair(signalDao)
            val preKeyId = preKey.id
            val preKeyRecord = PreKeyRecord(preKey.serialized)
            val preKeyPairPublicKey = preKeyRecord.keyPair.publicKey

            val signedPreKey = EncryptUtils.generatesSignedPreKeyPair(signalDao, identityKeyPair.privateKey)
            val signedPreKeyId = signedPreKey.id
            val signedPreKeyRecord = SignedPreKeyRecord(signedPreKey.serialized)
            val signedPreKeySignature = signedPreKeyRecord.signature

            val preKeyBundle =
                PreKeyBundle(
                    registrationId,
                    deviceId,
                    preKeyId,
                    preKeyPairPublicKey,
                    signedPreKeyId,
                    signedPreKeyRecord.keyPair.publicKey,
                    signedPreKeySignature,
                    identityKeyPair.publicKey,
                )

            val bytes = PreKeyBundleSerializer.encodePreKeyBundle(preKeyBundle)
            logger.debug { "${appInfo.appInstanceId} create preKeyBundle for $appInstanceId:\n ${preKeyBundle.getDescString()}" }
            successResponse(call, DataContent(bytes))
        }
    }

    post("/sync/createSession") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val dataContent = call.receive(DataContent::class)
            val bytes = dataContent.data
            signalProcessorCache.removeSignalMessageProcessor(appInstanceId)
            val processor = signalProcessorCache.getSignalMessageProcessor(appInstanceId)
            val preKeySignalMessage = PreKeySignalMessage(bytes)

            val signedPreKeyId = preKeySignalMessage.signedPreKeyId

            if (signalProtocolStore.containsSignedPreKey(signedPreKeyId)) {
                signalProtocolStore.saveIdentity(
                    processor.signalProtocolAddress,
                    preKeySignalMessage.identityKey,
                )
                val decrypt = processor.decrypt(preKeySignalMessage)

                try {
                    val syncInfo = DesktopJsonUtils.JSON.decodeFromString<SyncInfo>(String(decrypt, Charsets.UTF_8))
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

    post("/sync/heartbeat") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val targetAppInstanceId = call.request.headers["targetAppInstanceId"]
            if (targetAppInstanceId != appInfo.appInstanceId) {
                logger.debug { "heartbeat targetAppInstanceId $targetAppInstanceId not match ${appInfo.appInstanceId}" }
                failResponse(call, StandardErrorCode.SYNC_NOT_MATCH_APP_INSTANCE_ID.toErrorCode())
                return@let
            }
            val dataContent = call.receive(DataContent::class)
            val bytes = dataContent.data
            val processor = signalProcessorCache.getSignalMessageProcessor(appInstanceId)
            val signalMessage = SignalMessage(bytes)
            val decrypt = processor.decrypt(signalMessage)

            try {
                val syncInfo = DesktopJsonUtils.JSON.decodeFromString<SyncInfo>(String(decrypt, Charsets.UTF_8))
                // todo check diff time to update
                syncManager.updateSyncInfo(syncInfo)
                logger.debug { "$appInstanceId heartbeat to ${appInfo.appInstanceId} success" }
                successResponse(call)
            } catch (e: Exception) {
                logger.error(e) { "$appInstanceId heartbeat to ${appInfo.appInstanceId} fail" }
                failResponse(call, StandardErrorCode.SIGNAL_EXCHANGE_FAIL.toErrorCode())
            }
        }
    }

    get("/sync/showToken") {
        val appWindowManager = koinApplication.koin.get<AppWindowManager>()
        val appTokenService = koinApplication.koin.get<AppTokenService>()

        appTokenService.showToken = true
        appWindowManager.showMainWindow = true
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
            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
            signalProtocolStore.getIdentity(signalProtocolAddress)?.let {
                logger.debug { "${appInfo.appInstanceId} isTrust $appInstanceId" }
                successResponse(call)
            } ?: run {
                logger.debug { "${appInfo.appInstanceId} not trust $appInstanceId in /sync/isTrust api" }
                failResponse(call, StandardErrorCode.SIGNAL_UNTRUSTED_IDENTITY.toErrorCode(), "not trust $appInstanceId")
            }
        }
    }

    post("/sync/trust") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val requestTrust = call.receive(RequestTrust::class)

            val appTokenService = koinApplication.koin.get<AppTokenService>()

            if (requestTrust.token == String(appTokenService.token).toInt()) {
                val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                signalProtocolStore.saveIdentity(
                    signalProtocolAddress,
                    IdentityKey(requestTrust.identityKey),
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

    get("/sync/notifyExit") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncManager.markExit(appInstanceId)
            successResponse(call)
        }
    }

    get("/sync/notifyRemove") {
        getAppInstanceId(call)?.let { appInstanceId ->
            syncManager.removeSyncHandler(appInstanceId)
            successResponse(call)
        }
    }
}

fun PreKeyBundle.getDescString(): String {
    return "PreKeyBundle(registrationId=$registrationId, " +
        "deviceId=$deviceId, " +
        "preKeyId=$preKeyId, " +
        "preKey=$preKey, " +
        "signedPreKeyId=$signedPreKeyId, " +
        "signedPreKey=$signedPreKey, " +
        "signedPreKeySignature=$signedPreKeySignature, " +
        "identityKey=$identityKey)"
}
