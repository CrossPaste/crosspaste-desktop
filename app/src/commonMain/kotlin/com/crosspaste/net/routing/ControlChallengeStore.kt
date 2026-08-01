package com.crosspaste.net.routing

import com.crosspaste.dto.sync.ControlChallenge
import com.crosspaste.utils.DateUtils.nowEpochMilliseconds
import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

class ControlChallengeStore(
    private val ttlMillis: Long = DEFAULT_TTL.inWholeMilliseconds,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val now: () -> Long = ::nowEpochMilliseconds,
) {
    private data class Entry(
        val peerAppInstanceId: String,
        val challenge: ControlChallenge,
        val createdAt: Long,
    )

    private val mutex = Mutex()
    private val entries = mutableMapOf<String, Entry>()

    suspend fun issue(peerAppInstanceId: String): ControlChallenge =
        mutex.withLock {
            evictExpiredLocked()
            if (entries.size >= maxEntries) {
                entries.minByOrNull { it.value.createdAt }?.let { entries.remove(it.key) }
            }
            val challenge =
                ControlChallenge(
                    id = CryptographyRandom.nextBytes(CHALLENGE_ID_SIZE),
                    nonce = CryptographyRandom.nextBytes(CHALLENGE_NONCE_SIZE),
                )
            entries[challenge.id.toHex()] = Entry(peerAppInstanceId, challenge, now())
            challenge.detachedCopy()
        }

    suspend fun consume(
        peerAppInstanceId: String,
        challengeId: ByteArray,
        challengeNonce: ByteArray,
    ): Boolean =
        mutex.withLock {
            evictExpiredLocked()
            val entry = entries.remove(challengeId.toHex()) ?: return@withLock false
            entry.peerAppInstanceId == peerAppInstanceId &&
                constantTimeEquals(entry.challenge.id, challengeId) &&
                constantTimeEquals(entry.challenge.nonce, challengeNonce)
        }

    private fun evictExpiredLocked() {
        val cutoff = now() - ttlMillis
        entries.entries.removeAll { it.value.createdAt <= cutoff }
    }

    private fun ControlChallenge.detachedCopy(): ControlChallenge = ControlChallenge(id.copyOf(), nonce.copyOf())

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun constantTimeEquals(
        first: ByteArray,
        second: ByteArray,
    ): Boolean {
        if (first.size != second.size) return false
        var difference = 0
        first.indices.forEach { index ->
            difference = difference or (first[index].toInt() xor second[index].toInt())
        }
        return difference == 0
    }

    companion object {
        private val DEFAULT_TTL = 30.seconds
        private const val DEFAULT_MAX_ENTRIES = 128
        private const val CHALLENGE_ID_SIZE = 16
        private const val CHALLENGE_NONCE_SIZE = 32
    }
}
