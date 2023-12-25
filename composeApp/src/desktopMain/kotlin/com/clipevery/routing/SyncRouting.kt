package com.clipevery.routing

import com.clipevery.Dependencies
import com.clipevery.dao.SyncInfoDao
import com.clipevery.dto.sync.RequestSyncInfo
import com.clipevery.dto.sync.SyncInfo
import com.clipevery.utils.decodeReceive
import com.clipevery.utils.successResponse
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SignalProtocolStore

fun Routing.syncRouting() {

    val koinApplication = Dependencies.koinApplication

    val syncInfoDao = koinApplication.koin.get<SyncInfoDao>()

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
        decodeReceive<String>(call, signalProtocolStore)
        successResponse(call)
    }
}