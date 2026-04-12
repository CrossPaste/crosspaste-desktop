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
        val localWeight = platformWeight(localPlatform)
        val remoteWeight = platformWeight(remotePlatform)

        return when {
            localWeight != remoteWeight -> localWeight > remoteWeight // higher weight initiates
            else -> localAppInstanceId < remoteAppInstanceId // same weight: smaller id initiates
        }
    }

    /**
     * Weight determines which side initiates:
     * - Extension (2): always initiates toward desktop and mobile
     * - Mobile (1): initiates toward desktop
     * - Desktop (0): never initiates toward higher-weight peers
     */
    private fun platformWeight(platform: Platform): Int =
        when {
            platform.isExtension() -> 2
            !platform.isDesktop() -> 1 // mobile
            else -> 0 // desktop
        }
}
