package com.clipevery.routing

import com.clipevery.Clipevery
import com.clipevery.app.AppInfo
import com.clipevery.app.AppWindowManager
import com.clipevery.dao.signal.SignalDao
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dto.sync.DataContent
import com.clipevery.dto.sync.RequestTrust
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.exception.StandardErrorCode
import com.clipevery.serializer.PreKeyBundleSerializer
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
import org.signal.libsignal.protocol.SessionCipher
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

    val syncRuntimeInfoDao = koinApplication.koin.get<SyncRuntimeInfoDao>()

    val signalProtocolStore = koinApplication.koin.get<SignalProtocolStore>()

    get("/sync/telnet") {
        successResponse(call)
    }

    get("/sync/preKeyBundle") {
        getAppInstanceId(call).let { appInstanceId ->

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
            logger.debug { "${appInfo.appInstanceId} create preKeyBundle for $appInstanceId:\n $preKeyBundle" }
            successResponse(call, DataContent(bytes))
        }
    }

    post("sync/exchangeSyncInfo") {
        getAppInstanceId(call).let { appInstanceId ->
            val dataContent = call.receive(DataContent::class)
            val bytes = dataContent.data
            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
            val identityKey = signalProtocolStore.getIdentity(signalProtocolAddress)
            val sessionCipher = SessionCipher(signalProtocolStore, signalProtocolAddress)
            var decrypt: ByteArray? = null
            if (identityKey != null) {
                try {
                    val signalMessage = SignalMessage(bytes)
                    decrypt = sessionCipher.decrypt(signalMessage)
                } catch (ignore: InvalidMessageException) {
                } catch (ignore: NoSessionException) {
                }
            }

            if (decrypt == null) {
                val preKeySignalMessage = PreKeySignalMessage(bytes)

                val signedPreKeyId = preKeySignalMessage.signedPreKeyId

                if (signalProtocolStore.containsSignedPreKey(signedPreKeyId)) {
                    signalProtocolStore.saveIdentity(
                        signalProtocolAddress,
                        preKeySignalMessage.identityKey,
                    )
                    decrypt = sessionCipher.decrypt(preKeySignalMessage)
                } else {
                    logger.debug { "$appInstanceId exchangeSyncInfo to ${appInfo.appInstanceId}, not contain signedPreKeyId" }
                    failResponse(call, StandardErrorCode.SIGNAL_INVALID_KEY_ID.toErrorCode())
                    return@let
                }
            }

            try {
                val syncInfo = DesktopJsonUtils.JSON.decodeFromString<SyncInfo>(String(decrypt!!, Charsets.UTF_8))
                syncRuntimeInfoDao.inertOrUpdate(syncInfo)
                logger.debug { "$appInstanceId exchangeSyncInfo to ${appInfo.appInstanceId} success" }
                successResponse(call)
            } catch (e: Exception) {
                logger.debug(e) { "$appInstanceId exchangeSyncInfo to ${appInfo.appInstanceId} fail" }
                failResponse(call, StandardErrorCode.SIGNAL_EXCHANGE_FAIL.toErrorCode())
            }
        }
    }

    get("/sync/showToken") {
        val appWindowManager = koinApplication.koin.get<AppWindowManager>()
        appWindowManager.showToken = true
        appWindowManager.showMainWindow = true
        logger.debug { "show token" }
    }

    get("/sync/isTrust") {
        getAppInstanceId(call).let { appInstanceId ->
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

    post("sync/trust") {
        getAppInstanceId(call).let { appInstanceId ->
            val requestTrust = call.receive(RequestTrust::class)

            val appWindowManager = koinApplication.koin.get<AppWindowManager>()

            if (requestTrust.token == String(appWindowManager.token).toInt()) {
                val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                signalProtocolStore.saveIdentity(
                    signalProtocolAddress,
                    requestTrust.identityKey,
                )
                appWindowManager.showToken = false
                logger.debug { "${appInfo.appInstanceId} to trust $appInstanceId" }
                successResponse(call)
            } else {
                logger.error { "token invalid: ${requestTrust.token}" }
                failResponse(call, StandardErrorCode.TOKEN_INVALID.toErrorCode())
            }
        }
    }
}

fun PreKeyBundle.toString() {
    "PreKeyBundle(registrationId=$registrationId, " +
        "deviceId=$deviceId, " +
        "preKeyId=$preKeyId, " +
        "preKey=$preKey, " +
        "signedPreKeyId=$signedPreKeyId, " +
        "signedPreKey=$signedPreKey, " +
        "signedPreKeySignature=$signedPreKeySignature, " +
        "identityKey=$identityKey)"
}
