package com.clipevery.net.clientapi

import io.ktor.http.*
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.state.PreKeyBundle

interface SyncClientApi {

    suspend fun getPreKeyBundle(toUrl: URLBuilder.(URLBuilder) -> Unit): PreKeyBundle?

    suspend fun exchangePreKey(
        sessionCipher: SessionCipher,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): Boolean

    suspend fun isTrust(toUrl: URLBuilder.(URLBuilder) -> Unit): Boolean

    suspend fun trust(
        token: Int,
        toUrl: URLBuilder.(URLBuilder) -> Unit,
    ): Boolean
}
