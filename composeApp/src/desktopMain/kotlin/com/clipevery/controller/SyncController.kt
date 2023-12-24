package com.clipevery.controller

import com.clipevery.Dependencies
import com.clipevery.dao.SyncInfoDao
import com.clipevery.dto.sync.RequestSyncInfo
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.signal.ClipIdentityKeyStore
import com.clipevery.utils.decodePreKeyBundle
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyStore

fun Routing.syncRouting() {

    val koinApplication = Dependencies.koinApplication

    val syncInfoDao = koinApplication.koin.get<SyncInfoDao>()

    val sessionStore = koinApplication.koin.get<SessionStore>()
    val preKeyStore = koinApplication.koin.get<PreKeyStore>()
    val signedPreKeyStore = koinApplication.koin.get<SignedPreKeyStore>()
    val identityKeyStore = koinApplication.koin.get<ClipIdentityKeyStore>()


    get("/sync") {
        val requestSyncInfos = call.receive<List<RequestSyncInfo>>()
        syncInfoDao.database.transaction {
            for (requestSyncInfo in requestSyncInfos) {
                val signalProtocolAddress = SignalProtocolAddress(requestSyncInfo.appInfo.appInstanceId, 1)
                val sessionBuilder = SessionBuilder(sessionStore, preKeyStore, signedPreKeyStore, identityKeyStore, signalProtocolAddress)
                val preKeyBundle = decodePreKeyBundle(requestSyncInfo.preKeyBundle)
                syncInfoDao.saveSyncInfo(SyncInfo(requestSyncInfo.appInfo, requestSyncInfo.endpointInfo))
                sessionBuilder.process(preKeyBundle)

            }
        }
        call.respond(status = HttpStatusCode.OK, message = "success")
    }
}