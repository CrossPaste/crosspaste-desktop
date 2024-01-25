package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.app.AppUI
import com.clipevery.dao.signal.SignalRealm
import com.clipevery.dao.sync.SyncRuntimeInfoDao
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.dto.sync.decodeRequestTrust
import com.clipevery.exception.StandardErrorCode
import com.clipevery.utils.decodeReceive
import com.clipevery.utils.encodePreKeyBundle
import com.clipevery.utils.failResponse
import com.clipevery.utils.getAppInstanceId
import com.clipevery.utils.successResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.ExperimentalSerializationApi
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UntrustedIdentityException
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.Objects

@OptIn(ExperimentalSerializationApi::class)
fun Routing.syncRouting() {

    val logger = KotlinLogging.logger {}

    val koinApplication = Dependencies.koinApplication

    val signalRealm = koinApplication.koin.get<SignalRealm>()

    val syncRuntimeInfoDao = koinApplication.koin.get<SyncRuntimeInfoDao>()

    val signalProtocolStore = koinApplication.koin.get<SignalProtocolStore>()

    post("/sync/syncInfos") {
        val syncInfos: List<SyncInfo> = decodeReceive(call, signalProtocolStore)
        syncRuntimeInfoDao.inertOrUpdate(syncInfos)
        successResponse(call)
    }

    get("/sync/telnet") {
        successResponse(call)
    }

    get("/sync/preKeyBundle") {
        getAppInstanceId(call).let { appInstanceId ->

            val identityKeyPair = signalProtocolStore.identityKeyPair
            val registrationId = signalProtocolStore.localRegistrationId
            val deviceId = 1
            val preKey = signalRealm.generatePreKeyPair()
            val preKeyId = preKey.id
            val preKeyRecord = PreKeyRecord(preKey.serialized)
            val preKeyPairPublicKey = preKeyRecord.keyPair.publicKey

            val signedPreKey = signalRealm.generatesSignedPreKeyPair(identityKeyPair.privateKey)
            val signedPreKeyId = signedPreKey.id
            val signedPreKeyRecord = SignedPreKeyRecord(signedPreKey.serialized)
            signedPreKeyRecord.keyPair.publicKey
            val signedPreKeySignature = signedPreKeyRecord.signature

            val preKeyBundle = PreKeyBundle(
                registrationId,
                deviceId,
                preKeyId,
                preKeyPairPublicKey,
                signedPreKeyId,
                signedPreKeyRecord.keyPair.publicKey,
                signedPreKeySignature,
                signalProtocolStore.identityKeyPair.publicKey
            )

            val bytes = encodePreKeyBundle(preKeyBundle)
            successResponse(call, bytes)
        }
    }

    post("sync/exchangePreKey") {
        getAppInstanceId(call).let { appInstanceId ->
            val bytes = call.receive<ByteArray>()
            try {
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
                        failResponse(call, "invalid key id", status = HttpStatusCode.ExpectationFailed)
                        return@let
                    }
                }

                if (Objects.equals("exchange", String(decrypt!!, Charsets.UTF_8))) {
                    val ciphertextMessage = sessionCipher.encrypt("exchange".toByteArray(Charsets.UTF_8))
                    successResponse(call, ciphertextMessage.serialize())
                } else {
                    failResponse(call, "exchange fail", status = HttpStatusCode.ExpectationFailed)
                }
            } catch (e: InvalidMessageException) {
                failResponse(call, "invalid message", exception = e, logger = logger, errorCodeSupplier = StandardErrorCode.INVALID_PARAMETER, status = HttpStatusCode.ExpectationFailed)
            } catch (e: InvalidKeyIdException) {
                failResponse(call, "invalid key id", exception = e, logger = logger, errorCodeSupplier = StandardErrorCode.INVALID_PARAMETER, status = HttpStatusCode.ExpectationFailed)
            } catch (e: InvalidKeyException) {
                failResponse(call, "invalid key", exception = e, logger = logger, errorCodeSupplier = StandardErrorCode.INVALID_PARAMETER, status = HttpStatusCode.ExpectationFailed)
            } catch (e: UntrustedIdentityException) {
                failResponse(call, "untrusted identity", exception = e, logger = logger, errorCodeSupplier = StandardErrorCode.INVALID_PARAMETER, status = HttpStatusCode.ExpectationFailed)
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
            } ?: failResponse(call, "not trust", status = HttpStatusCode.ExpectationFailed)
        }
    }

    post("sync/trust") {
        getAppInstanceId(call).let { appInstanceId ->
            val bytes = call.receive<ByteArray>()
            val requestTrust = decodeRequestTrust(bytes)

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
                failResponse(call, "token isn't right", status = HttpStatusCode.ExpectationFailed)
            }
        }
    }
}

