package com.clipevery.net.clientapi

import com.clipevery.dto.sync.SyncInfo
import io.ktor.http.*
import org.signal.libsignal.protocol.SessionCipher

interface SyncClientApi {

    suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult

    suspend fun exchangeSyncInfo(
        syncInfo: SyncInfo,
        sessionCipher: SessionCipher,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult

    suspend fun isTrust(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult

    suspend fun trust(
        token: Int,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): ClientApiResult

    suspend fun showToken(toUrl: URLBuilder.(URLBuilder) -> Unit): ClientApiResult

    suspend fun notifyExit(toUrl: URLBuilder.(URLBuilder) -> Unit)

    suspend fun notifyRemove(toUrl: URLBuilder.(URLBuilder) -> Unit)
}
