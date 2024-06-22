package com.clipevery.routing

import com.clipevery.Clipevery
import com.clipevery.app.AppInfo
import com.clipevery.app.AppTokenService
import com.clipevery.app.AppWindowManager
import com.clipevery.dao.signal.SignalDao
import com.clipevery.dto.sync.DataContent
import com.clipevery.dto.sync.RequestTrust
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.exception.StandardErrorCode
import com.clipevery.serializer.PreKeyBundleSerializer
import com.clipevery.signal.SignalProcessorCache
import com.clipevery.sync.SyncManager
import com.clipevery.utils.DesktopJsonUtils
import com.clipevery.utils.failResponse
import com.clipevery.utils.getAppInstanceId
import com.clipevery.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

fun Routing.syncRouting() {
    val logger = KotlinLogging.logger {}

    val koinApplication = Clipevery.koinApplication

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
            val preKey = signalDao.generatePreKeyPair()
            val preKeyId = preKey.id
            val preKeyRecord = PreKeyRecord(preKey.serialized)
            val preKeyPairPublicKey = preKeyRecord.keyPair.publicKey

            val signedPreKey = signalDao.generatesSignedPreKeyPair(identityKeyPair.privateKey)
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

    post("/sync/exchangeSyncInfo") {
        getAppInstanceId(call)?.let { appInstanceId ->
            val dataContent = call.receive(DataContent::class)
            val bytes = dataContent.data
            val processor = signalProcessorCache.getSignalMessageProcessor(appInstanceId)
            val identityKey = signalProtocolStore.getIdentity(processor.signalProtocolAddress)
            var decrypt: ByteArray? = null
            if (identityKey != null) {
                try {
                    val signalMessage = SignalMessage(bytes)
                    decrypt = processor.decrypt(signalMessage)
                } catch (ignore: InvalidMessageException) {
                } catch (ignore: NoSessionException) {
                }
            }

            if (decrypt == null) {
                val preKeySignalMessage = PreKeySignalMessage(bytes)

                val signedPreKeyId = preKeySignalMessage.signedPreKeyId

                if (signalProtocolStore.containsSignedPreKey(signedPreKeyId)) {
                    signalProtocolStore.saveIdentity(
                        processor.signalProtocolAddress,
                        preKeySignalMessage.identityKey,
                    )
                    decrypt = processor.decrypt(preKeySignalMessage)
                } else {
                    logger.error { "$appInstanceId exchangeSyncInfo to ${appInfo.appInstanceId}, not contain signedPreKeyId" }
                    failResponse(call, StandardErrorCode.SIGNAL_INVALID_KEY_ID.toErrorCode())
                    return@let
                }
            }

            try {
                val syncInfo = DesktopJsonUtils.JSON.decodeFromString<SyncInfo>(String(decrypt, Charsets.UTF_8))
                syncManager.updateSyncInfo(syncInfo)
                logger.debug { "$appInstanceId exchangeSyncInfo to ${appInfo.appInstanceId} success" }
                successResponse(call)
            } catch (e: Exception) {
                logger.error(e) { "$appInstanceId exchangeSyncInfo to ${appInfo.appInstanceId} fail" }
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
                    requestTrust.identityKey,
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
