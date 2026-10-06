package com.crosspaste.sync

/**
 * The set of devices known to already hold a synced paste, or to be receiving it
 * in the same send round. It travels with every desktop-to-desktop sync, and a
 * relaying device never forwards the paste to a device in the set while adding
 * its own targets before passing it on. The set only grows hop by hop, so relay
 * rounds cannot loop. Peers that predate it send nothing, which reads as "only
 * the sender has it".
 */
object RelaySeen {

    const val HEADER: String = "X-Relay-Seen"

    // Peer-supplied: bound it so a malformed value cannot bloat the stored task.
    private const val MAX_IDS = 256

    fun encode(appInstanceIds: Set<String>): String = appInstanceIds.joinToString(",")

    fun decode(value: String?): Set<String> = decode(value?.split(','))

    fun decode(appInstanceIds: List<String>?): Set<String> =
        appInstanceIds
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_IDS)
            ?.toSet()
            ?: emptySet()
}
