package com.crosspaste.sync

import kotlin.jvm.JvmInline

/**
 * Two distinct 6-digit pairing credentials that must never be interchanged.
 *
 * [QrBearerToken] is a short-lived *random* value the display side generates and shows
 * (embedded in its QR / on-screen pairing code); the receiver proves possession by sending
 * it back to `POST /sync/trust`, where `sameToken` compares it against the displayed value.
 * It is device-independent and known before any key exchange.
 *
 * [SasCode] is *derived from both peers' public keys* (CryptographyUtils.computeSAS) and only
 * exists after the ECDH key exchange; the user compares it across both screens, and it is
 * confirmed via the `POST /sync/trust/v2/exchange` + `/confirm` endpoints. Because it depends
 * on the scanning device's key, a QR — built before the scan — can never carry it.
 *
 * Keeping them as separate types makes "feed a scanned QR bearer token into the SAS
 * comparison" — a deterministic failure for pairingVersion>=2 peers — unrepresentable at the
 * call site, instead of relying on an internal pairingVersion `when`.
 */
@JvmInline
value class QrBearerToken(
    val value: Int,
)

@JvmInline
value class SasCode(
    val value: Int,
)
