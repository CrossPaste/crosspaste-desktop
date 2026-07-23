package com.crosspaste.pairing.v3

/**
 * Derives the device-bound six-digit PIN for one token generation.
 *
 * `PIN = UniformDecimal6(HMAC-SHA256(R, pinContext || counter))` where `R` is a fresh
 * 256-bit random secret per generation and the counter implements rejection sampling:
 * the 32-byte material is scanned in big-endian u32 windows, a window is accepted when
 * it is below `floor(2^32 / 10^6) * 10^6`, and if all eight windows are rejected the
 * counter increments and fresh material is derived. The result is uniform over
 * 000000..999999 and preserves leading zeros.
 */
object PairingPinGenerator {

    private const val PIN_SPACE = 1_000_000L

    private const val REJECTION_BOUND = (4_294_967_296L / PIN_SPACE) * PIN_SPACE

    suspend fun derivePin(
        secret: ByteArray,
        pinContext: ByteArray,
    ): CharArray {
        require(secret.size == PairingV3.PIN_SECRET_SIZE) { "pin secret must be ${PairingV3.PIN_SECRET_SIZE} bytes" }
        var counter = 0
        while (true) {
            val material = PairingKeySchedule.hmacSha256(secret, pinContext + CanonicalWriter.u32(counter))
            uniformDecimal6(material)?.let { pin ->
                material.fill(0)
                return formatPin(pin)
            }
            material.fill(0)
            counter++
        }
    }

    internal fun uniformDecimal6(material: ByteArray): Int? {
        var offset = 0
        while (offset + 4 <= material.size) {
            val value =
                ((material[offset].toLong() and 0xFF) shl 24) or
                    ((material[offset + 1].toLong() and 0xFF) shl 16) or
                    ((material[offset + 2].toLong() and 0xFF) shl 8) or
                    (material[offset + 3].toLong() and 0xFF)
            if (value < REJECTION_BOUND) {
                return (value % PIN_SPACE).toInt()
            }
            offset += 4
        }
        return null
    }

    internal fun formatPin(pin: Int): CharArray {
        require(pin in 0 until PIN_SPACE.toInt()) { "pin out of range: $pin" }
        val result = CharArray(PairingV3.PIN_LENGTH)
        var remaining = pin
        for (i in PairingV3.PIN_LENGTH - 1 downTo 0) {
            result[i] = '0' + (remaining % 10)
            remaining /= 10
        }
        return result
    }
}
