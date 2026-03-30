package com.crosspaste.net.ws

import com.crosspaste.platform.Platform

/**
 * Deterministic rule for WebSocket connection initiation.
 * Both sides compute the same result independently, avoiding duplicate connections.
 *
 * Rules:
 * 1. Mobile → Desktop: mobile always initiates (natural for mobile lifecycle)
 * 2. Same type (desktop↔desktop, mobile↔mobile): smaller appInstanceId initiates
 */
object WsConnectionPolicy {

    fun shouldInitiate(
        localAppInstanceId: String,
        localPlatform: Platform,
        remoteAppInstanceId: String,
        remotePlatform: Platform,
    ): Boolean {
        val localIsMobile = !localPlatform.isDesktop()
        val remoteIsMobile = !remotePlatform.isDesktop()

        return when {
            localIsMobile && !remoteIsMobile -> true // mobile → desktop: mobile initiates
            !localIsMobile && remoteIsMobile -> false // desktop ← mobile: wait for mobile
            else -> localAppInstanceId < remoteAppInstanceId // same type: smaller id initiates
        }
    }
}
