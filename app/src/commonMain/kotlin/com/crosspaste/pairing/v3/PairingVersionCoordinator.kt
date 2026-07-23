package com.crosspaste.pairing.v3

import com.crosspaste.utils.StripedMutex

/**
 * Serializes protocol selection for one peer so legacy trust cannot complete
 * concurrently with the creation of a v3 session.
 */
class PairingVersionCoordinator {

    private val peerMutex = StripedMutex()

    suspend fun <T> withPeerLock(
        appInstanceId: String,
        action: suspend () -> T,
    ): T = peerMutex.withLock(appInstanceId, action)
}
