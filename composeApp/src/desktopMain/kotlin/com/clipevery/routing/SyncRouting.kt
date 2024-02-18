package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.app.AppUI
import com.clipevery.dao.signal.ClipIdentityKey
import com.clipevery.dao.signal.SignalDao
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dto.sync.DataContent
import com.clipevery.dto.sync.RequestTrust
import com.clipevery.dto.sync.RequestTrustSyncInfo
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.exception.StandardErrorCode
import com.clipevery.net.CheckAction
import com.clipevery.net.ClientHandlerManager
import com.clipevery.net.DeviceRefresher
import com.clipevery.utils.encodePreKeyBundle
import com.clipevery.utils.failResponse
import com.clipevery.utils.getAppInstanceId
import com.clipevery.utils.successResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.Objects

fun Routing.syncRouting() {

    val koinApplication = Dependencies.koinApplication

    val signalDao = koinApplication.koin.get<SignalDao>()

    val syncRuntimeInfoDao = koinApplication.koin.get<SyncRuntimeInfoDao>()

    val signalProtocolStore = koinApplication.koin.get<SignalProtocolStore>()

    val deviceRefresher = koinApplication.koin.get<DeviceRefresher>()

    val clientHandlerManager = koinApplication.koin.get<ClientHandlerManager>()

    post("/sync/syncInfos") {
        getAppInstanceId(call).let { appInstanceId ->
            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
            signalProtocolStore.getIdentity(signalProtocolAddress)?.let {
                val requestTrustSyncInfos: List<RequestTrustSyncInfo> = call.receive()
                val syncInfos: List<SyncInfo> = requestTrustSyncInfos.map { it.syncInfo }
                val identityKeys = requestTrustSyncInfos.map { ClipIdentityKey(it.syncInfo.appInfo.appInstanceId, it.identityKey.serialize()) }
                syncRuntimeInfoDao.inertOrUpdate(syncInfos)
                signalDao.saveIdentities(identityKeys)
                for (syncInfo in syncInfos) {
                    clientHandlerManager.addHandler(syncInfo.appInfo.appInstanceId)
                }
                deviceRefresher.refresh(CheckAction.CheckNonConnected)
                successResponse(call)
            } ?:  failResponse(call, StandardErrorCode.SIGNAL_UNTRUSTED_IDENTITY.toErrorCode(), "not trust $appInstanceId")
        }
    }

    get("/sync/telnet") {
        successResponse(call)
    }

    get("/sync/preKeyBundle") {
        getAppInstanceId(call).let { appInstanceId ->

            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)

            signalProtocolStore.getIdentity(signalProtocolAddress) ?: run {
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

            val preKeyBundle = PreKeyBundle(
                registrationId,
                deviceId,
                preKeyId,
                preKeyPairPublicKey,
                signedPreKeyId,
                signedPreKeyRecord.keyPair.publicKey,
                signedPreKeySignature,
                identityKeyPair.publicKey
            )

            val bytes = encodePreKeyBundle(preKeyBundle)
            successResponse(call, DataContent(bytes))
        }
    }

    post("sync/exchangePreKey") {
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
                } catch (ignore: InvalidMessageException) {}
            }

            if (decrypt == null) {
                val preKeySignalMessage = PreKeySignalMessage(bytes)

                val signedPreKeyId = preKeySignalMessage.signedPreKeyId

                if (signalProtocolStore.containsSignedPreKey(signedPreKeyId)) {
                    signalProtocolStore.saveIdentity(
                        signalProtocolAddress,
                        preKeySignalMessage.identityKey
                    )
                    decrypt = sessionCipher.decrypt(preKeySignalMessage)
                } else {
                    failResponse(call, StandardErrorCode.SIGNAL_INVALID_KEY_ID.toErrorCode())
                    return@let
                }
            }

            if (Objects.equals("exchange", String(decrypt!!, Charsets.UTF_8))) {
                val ciphertextMessage = sessionCipher.encrypt("exchange".toByteArray(Charsets.UTF_8))
                successResponse(call, DataContent(ciphertextMessage.serialize()))
            } else {
                failResponse(call, StandardErrorCode.SIGNAL_EXCHANGE_FAIL.toErrorCode())
            }
        }
    }

    get("/sync/showToken") {
        val appUI = koinApplication.koin.get<AppUI>()
        appUI.showToken = true
        appUI.showWindow = true
    }

    get("/sync/isTrust") {
        getAppInstanceId(call).let { appInstanceId ->
            val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
            signalProtocolStore.getIdentity(signalProtocolAddress)?.let {
                successResponse(call)
            } ?: failResponse(call, StandardErrorCode.SIGNAL_UNTRUSTED_IDENTITY.toErrorCode(), "not trust $appInstanceId")
        }
    }

    post("sync/trust") {
        getAppInstanceId(call).let { appInstanceId ->
            val requestTrust = call.receive(RequestTrust::class)

            val appUI = koinApplication.koin.get<AppUI>()

            if (requestTrust.token == String(appUI.token).toInt()) {
                val signalProtocolAddress = SignalProtocolAddress(appInstanceId, 1)
                signalProtocolStore.saveIdentity(
                    signalProtocolAddress,
                    requestTrust.identityKey
                )
                appUI.showToken = false
                successResponse(call)
            } else {
                failResponse(call, StandardErrorCode.TOKEN_INVALID.toErrorCode())
            }
        }
    }
}

