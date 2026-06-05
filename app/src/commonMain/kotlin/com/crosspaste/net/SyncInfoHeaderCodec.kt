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
 * Two sites must agree byte-for-byte on this format:
 *  - clients attach [HEADER] on `/sync/telnet` to push their current (subnet-matched)
 *    address so a peer learns it without waiting for the next mDNS round (#4509 phase 3),
 *  - the server decodes it back in `SyncRouting` (also reused by the trust handshake).
 *
 * Keeping encode/decode in one object is what guarantees that symmetry.
 */
object SyncInfoHeaderCodec {

    const val HEADER: String = "crosspaste-sync-info"

    @OptIn(ExperimentalEncodingApi::class)
    fun encode(syncInfo: SyncInfo): String =
        Base64.encode(getJsonUtils().JSON.encodeToString(syncInfo).encodeToByteArray())

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(encoded: String): SyncInfo? =
        runCatching {
            getJsonUtils().JSON.decodeFromString<SyncInfo>(
                Base64.decode(encoded).decodeToString(),
            )
        }.getOrNull()
}
