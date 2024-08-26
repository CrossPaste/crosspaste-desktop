package com.crosspaste.net.clientapi

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.signal.SignalMessageProcessor
import io.ktor.http.*

interface SyncClientApi {

    suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult

    suspend fun syncInfo(): ClientApiResult

    suspend fun createSession(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult

    suspend fun heartbeat(
        syncInfo: SyncInfo,
        signalMessageProcessor: SignalMessageProcessor,
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult

    suspend fun isTrust(
        targetAppInstanceId: String,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult

    suspend fun trust(
        token: Int,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult

    suspend fun showToken(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult

    suspend fun notifyExit(toUrl: URLBuilder.(URLBuilder) -> Unit)

    suspend fun notifyRemove(toUrl: URLBuilder.(URLBuilder) -> Unit)
}
