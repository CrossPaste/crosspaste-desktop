package com.crosspaste.net

import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.utils.getJsonUtils
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Wire codec for advertising a [SyncInfo] inside an HTTP header. The payload is the
 * JSON encoding of the SyncInfo, Base64-wrapped so it survives as a single header value.
 *
 * Three sites must agree byte-for-byte on this format:
 *  - desktop/mobile clients attach [HEADER] on `/sync/telnet` to push their current
 *    (subnet-matched) address so a peer learns it without waiting for the next mDNS round
 *    (#4509 phase 3),
 *  - the server decodes it back in `SyncRouting` (also reused by the trust handshake),
 *  - the web client (`web/src/shared/api/sync.ts`) builds the same header via
 *    `btoa(JSON.stringify(syncInfo))`.
 *
 * Keeping encode/decode in one object is what guarantees that symmetry on this side — any
 * change to [HEADER] or the encoding must be mirrored in the web client.
 */
object SyncInfoHeaderCodec {

    const val HEADER: String = "crosspaste-sync-info"

    @OptIn(ExperimentalEncodingApi::class)
    fun encode(syncInfo: SyncInfo): String =
        Base64.encode(getJsonUtils().JSON.encodeToString(syncInfo).encodeToByteArray())

    /**
     * Decode the header value, throwing on malformed input. Use when the caller wants to
     * log *why* a header failed to parse; prefer [decode] when a null is enough.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun decodeOrThrow(encoded: String): SyncInfo =
        getJsonUtils().JSON.decodeFromString<SyncInfo>(
            Base64.decode(encoded).decodeToString(),
        )

    fun decode(encoded: String): SyncInfo? = runCatching { decodeOrThrow(encoded) }.getOrNull()
}
