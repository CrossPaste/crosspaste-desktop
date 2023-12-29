package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.dao.SignalStoreDao
import com.clipevery.dao.SyncInfoDao
import com.clipevery.dto.sync.RequestSyncInfo
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.utils.getAppInstanceId
import com.clipevery.utils.successResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord

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

            successResponse(call, preKeyBundle)
        }

    }
}