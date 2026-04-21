package com.crosspaste.dto.notice

import kotlinx.serialization.Serializable

/**
 * Notice sent to a receiver (typically the Chrome extension) when the sender
 * has rejected a paste because it exceeds the receiver's size limits.
 *
 * The receiver can surface this as a user-visible notification rather than
 * silently dropping the paste.
 */
@Serializable
data class OversizePasteNotice(
    val reason: Reason,
    /** File name of the offending file; null when [reason] is [Reason.TOTAL_TOO_LARGE]. */
    val fileName: String? = null,
    /** Actual size in bytes of the offending unit (single file or total paste). */
    val actualSize: Long,
    /** Limit in bytes that was exceeded. */
    val sizeLimitBytes: Long,
) {
    enum class Reason {
        FILE_TOO_LARGE,
        TOTAL_TOO_LARGE,
    }
}
