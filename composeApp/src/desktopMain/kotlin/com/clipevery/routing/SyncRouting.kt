package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.dao.SignalStoreDao
import com.clipevery.dao.SyncInfoDao
import com.clipevery.dto.sync.RequestSyncInfo
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.utils.encodePreKeyBundle
import com.clipevery.utils.failResponse
import com.clipevery.utils.getAppInstanceId
import com.clipevery.utils.successResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.signal.libsignal.protocol.InvalidKeyException
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.InvalidMessageException
import org.signal.libsignal.protocol.SessionBuilder
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

fun Routing.syncRouting() {

    val koinApplication = Dependencies.koinApplication

    val syncInfoDao = koinApplication.koin.get<SyncInfoDao>()

    val signalStoreDao = koinApplication.koin.get<SignalStoreDao>()

    val signalProtocolStore = koinApplication.koin.get<SignalProtocolStore>()

    get("/sync/receive") {
        val requestSyncInfos = call.receive<List<RequestSyncInfo>>()
        syncInfoDao.database.transaction {
            for (requestSyncInfo in requestSyncInfos) {
                val signalProtocolAddress = SignalProtocolAddress(requestSyncInfo.appInfo.appInstanceId, 1)
                val sessionBuilder = SessionBuilder(signalProtocolStore, signalProtocolAddress)
                val preKeyBundle = requestSyncInfo.preKeyBundle
                syncInfoDao.saveSyncInfo(SyncInfo(requestSyncInfo.appInfo, requestSyncInfo.endpointInfo))
                sessionBuilder.process(preKeyBundle)
            }
        }
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
            val preKey = signalStoreDao.generatePreKeyPair()
            val preKeyId = preKey.id.toInt()
            val preKeyRecord = PreKeyRecord(preKey.serialized)
            val preKeyPairPublicKey = preKeyRecord.keyPair.publicKey

            val signedPreKey = signalStoreDao.generatesSignedPreKeyPair(identityKeyPair.privateKey)
            val signedPreKeyId = signedPreKey.id.toInt()
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
                val decrypt = if (identityKey == null) {
                    val preKeySignalMessage = PreKeySignalMessage(bytes)

                    val signedPreKeyId = preKeySignalMessage.signedPreKeyId

                    if (signalProtocolStore.containsSignedPreKey(signedPreKeyId)) {
                        signalProtocolStore.saveIdentity(
                            signalProtocolAddress,
                            preKeySignalMessage.identityKey
                        )
                        sessionCipher.decrypt(preKeySignalMessage)
                    } else {
                        failResponse(call, "invalid key id", status = HttpStatusCode.ExpectationFailed)
                        return@let
                    }

                } else {
                    sessionCipher.decrypt(SignalMessage(bytes))
                }

                if (Objects.equals("exchange", String(decrypt, Charsets.UTF_8))) {
                    val ciphertextMessage = sessionCipher.encrypt("exchange".toByteArray(Charsets.UTF_8))
                    successResponse(call, ciphertextMessage.serialize())
                } else {
                    failResponse(call, "exchange fail", status = HttpStatusCode.ExpectationFailed)
                }
            } catch (e: InvalidMessageException) {
                failResponse(call, "invalid message", status = HttpStatusCode.ExpectationFailed)
            } catch (e: InvalidKeyIdException) {
                failResponse(call, "invalid key id", status = HttpStatusCode.ExpectationFailed)
            } catch (e: InvalidKeyException) {
                failResponse(call, "invalid key", status = HttpStatusCode.ExpectationFailed)
            } catch (e: UntrustedIdentityException) {
                failResponse(call, "untrusted identity", status = HttpStatusCode.ExpectationFailed)
            }
        }
    }
}