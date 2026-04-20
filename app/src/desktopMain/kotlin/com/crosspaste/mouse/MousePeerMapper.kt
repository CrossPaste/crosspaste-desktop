package com.crosspaste.mouse

import com.crosspaste.db.sync.SyncRuntimeInfo

object MousePeerMapper {

    /**
     * Convert paired-device rows into daemon peers.
     * - Drops devices with no reachable host address (still sync-negotiating).
     * - Drops devices the user has not yet placed in the arrangement canvas.
     * - Uses [SyncRuntimeInfo.appInstanceId] as the peer key (daemon's `device_id`),
     *   matching what MouseLayoutStore uses.
     * - Never sets fingerprint (product decision: desktop is the trust authority).
     */
    fun map(
        syncs: List<SyncRuntimeInfo>,
        layout: Map<String, Position>,
    ): List<IpcPeer> =
        syncs.mapNotNull { sri ->
            val host = sri.connectHostAddress ?: return@mapNotNull null
            val position = layout[sri.appInstanceId] ?: return@mapNotNull null
            IpcPeer(
                name = sri.deviceName.ifBlank { sri.appInstanceId },
                address = "$host:${sri.port}",
                position = position,
                deviceId = sri.appInstanceId,
            )
        }
}
