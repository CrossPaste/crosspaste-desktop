package com.crosspaste.db.sync

import kotlinx.serialization.Serializable

@Serializable
data class HostInfo(
    val networkPrefixLength: Short,
    val hostAddress: String,
    val lastSeen: Long = 0L,
) {
    companion object {
        // Cap on retained addresses per peer. Bounds ghost accumulation (#4499) while
        // keeping enough recent addresses to cover multiple interfaces (wifi/ethernet/VPN)
        // plus a little IP history.
        const val MAX_RECENT_HOST_INFO: Int = 8

        /**
         * Merge [incoming] advertised addresses into [existing], newest-first.
         *
         * Recency ([lastSeen]) is stamped on receipt with [now] — the value carried over
         * the wire is meaningless across devices' clocks. Incoming addresses replace any
         * same-address existing entry with a fresh [now]; the result is ordered by
         * lastSeen descending and capped at [max] so a peer's address list can never grow
         * unbounded. Eviction is purely capacity-based (LRU): a stable, still-connected
         * device is never aged out by a clock, and ghost addresses that stop being
         * advertised sink to the bottom and fall off as new addresses arrive.
         */
        fun mergeRecent(
            existing: List<HostInfo>,
            incoming: List<HostInfo>,
            now: Long,
            max: Int = MAX_RECENT_HOST_INFO,
        ): List<HostInfo> {
            // De-dup incoming by address (a peer / TXT record may advertise the same
            // address more than once); stamp the survivors fresh.
            val fresh = incoming.distinctBy { it.hostAddress }.map { it.copy(lastSeen = now) }
            val incomingAddresses = fresh.mapTo(HashSet()) { it.hostAddress }
            val retained = existing.filterNot { it.hostAddress in incomingAddresses }
            // Sort newest-first, then collapse any remaining duplicate addresses (keeping
            // the freshest — e.g. legacy `existing` rows that held the same address under
            // different prefixes), and finally cap.
            return (fresh + retained)
                .sortedByDescending { it.lastSeen }
                .distinctBy { it.hostAddress }
                .take(max)
        }
    }
}
