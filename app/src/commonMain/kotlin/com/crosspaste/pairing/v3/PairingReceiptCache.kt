package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingCommitAckV3
import com.crosspaste.utils.DateUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bounded cache of authenticated commit receipts for idempotent retries.
 *
 * First write wins, permanently for the lifetime of the entry: a byte-identical
 * commit retry returns the ORIGINAL authenticated receipt; a commit with different
 * bytes for the same session is a conflict and never overwrites. Record and lookup
 * are one atomic operation under a mutex, so concurrent duplicate commits cannot
 * observe partial state. After TTL expiry a late retry is safely rejected upstream
 * (the session is already terminal).
 */
class PairingReceiptCache(
    private val ttlMillis: Long = PairingV3.DEFAULT_RECEIPT_TTL.inWholeMilliseconds,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val nowEpochMillis: () -> Long = { DateUtils.nowEpochMilliseconds() },
) {

    sealed interface Record {

        /** This call stored the receipt; the caller's ack is now the canonical one. */
        data class Inserted(
            val ack: PairingCommitAckV3,
        ) : Record

        /** An identical commit was already recorded; return the ORIGINAL receipt. */
        data class Hit(
            val ack: PairingCommitAckV3,
        ) : Record

        /** A different commit exists for this session; must be rejected, never overwritten. */
        data object Conflict : Record
    }

    sealed interface Lookup {

        data class Hit(
            val ack: PairingCommitAckV3,
        ) : Lookup

        data object Conflict : Lookup

        data object Miss : Lookup
    }

    private class Entry(
        val commitMacHex: String,
        val ack: PairingCommitAckV3,
        val storedAt: Long,
    )

    private val mutex = Mutex()

    private val entries = mutableMapOf<String, Entry>()

    /**
     * Atomically records the first receipt for a session or resolves a retry.
     * The eviction policy only ever creates room for NEW sessions; an existing
     * entry is never replaced by a different commit.
     */
    suspend fun recordOrLookup(
        sessionId: String,
        commitMacHex: String,
        ack: PairingCommitAckV3,
    ): Record =
        mutex.withLock {
            evictDueLocked()
            val existing = entries[sessionId]
            when {
                existing == null -> {
                    if (entries.size >= maxEntries) {
                        entries
                            .minByOrNull { entry -> entry.value.storedAt }
                            ?.let { oldest -> entries.remove(oldest.key) }
                    }
                    entries[sessionId] = Entry(commitMacHex, ack, nowEpochMillis())
                    Record.Inserted(ack)
                }

                existing.commitMacHex == commitMacHex -> Record.Hit(existing.ack)

                else -> Record.Conflict
            }
        }

    /** Read-only resolution for retries arriving after session keys were cleared. */
    suspend fun lookup(
        sessionId: String,
        commitMacHex: String,
    ): Lookup =
        mutex.withLock {
            evictDueLocked()
            val entry = entries[sessionId]
            when {
                entry == null -> Lookup.Miss
                entry.commitMacHex == commitMacHex -> Lookup.Hit(entry.ack)
                else -> Lookup.Conflict
            }
        }

    /** Must only be called while holding [mutex]. */
    private fun evictDueLocked() {
        val cutoff = nowEpochMillis() - ttlMillis
        val dueKeys = entries.filter { entry -> entry.value.storedAt <= cutoff }.keys.toList()
        dueKeys.forEach { key -> entries.remove(key) }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES: Int = 32
    }
}
