package com.crosspaste.pairing.v3

import com.crosspaste.dto.pairing.v3.PairingV3ErrorCode
import com.crosspaste.exception.StandardErrorCode
import com.crosspaste.exception.standardErrorCodeMap

/**
 * Bridges the protocol-level [PairingV3ErrorCode] to the transport-level
 * [StandardErrorCode] used by `failResponse`/`FailResponse`. The two enums use
 * identical entry names on purpose; a test locks the 1:1 mapping.
 */
fun PairingV3ErrorCode.toStandardErrorCode(): StandardErrorCode = StandardErrorCode.valueOf(name)

/** Reverse mapping for client-side typed error handling. Null for non-v3 codes. */
fun pairingV3ErrorCodeOf(transportCode: Int): PairingV3ErrorCode? {
    val standard = standardErrorCodeMap[transportCode] as? StandardErrorCode ?: return null
    return PairingV3ErrorCode.entries.firstOrNull { entry -> entry.name == standard.name }
}
