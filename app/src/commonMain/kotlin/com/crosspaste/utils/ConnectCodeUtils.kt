package com.crosspaste.utils

/**
 * Crockford Base32 encoding for IP:port → short connection code.
 *
 * Encodes 6 bytes (IPv4 + port) into a 10-character code formatted as XXXXX-XXXXX.
 * Case-insensitive, excludes I/L/O/U to avoid human confusion.
 *
 * @see https://www.crockford.com/base32.html
 */
object ConnectCodeUtils {

    private const val ENCODE_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private val DECODE_MAP: Map<Char, Int> =
        buildMap {
            for (i in ENCODE_CHARS.indices) {
                put(ENCODE_CHARS[i], i)
                put(ENCODE_CHARS[i].lowercaseChar(), i)
            }
            // Common misreads
            put('O', 0)
            put('o', 0) // O → 0
            put('I', 1)
            put('i', 1) // I → 1
            put('L', 1)
            put('l', 1) // L → 1
        }

    /**
     * Encode an IPv4 address and port into a 10-char Crockford Base32 code.
     * Returns formatted as "XXXXX-XXXXX".
     */
    fun encode(
        ip: String,
        port: Int,
    ): String {
        val parts = ip.split(".").map { it.toIntOrNull() ?: -1 }
        require(parts.size == 4 && parts.all { it in 0..255 }) { "Invalid IPv4 address: $ip" }
        require(port in 1..65535) { "Invalid port: $port" }

        var value = 0L
        value = value or (parts[0].toLong() shl 40)
        value = value or (parts[1].toLong() shl 32)
        value = value or (parts[2].toLong() shl 24)
        value = value or (parts[3].toLong() shl 16)
        value = value or port.toLong()

        val chars = CharArray(10)
        var remaining = value
        for (i in 9 downTo 0) {
            chars[i] = ENCODE_CHARS[(remaining and 31).toInt()]
            remaining = remaining ushr 5
        }

        return "${chars.concatToString(0, 5)}-${chars.concatToString(5, 10)}"
    }

    /**
     * Decode a Crockford Base32 connection code back to IP and port.
     * Accepts with or without dash, case-insensitive.
     */
    fun decode(code: String): Pair<String, Int> {
        val cleaned = code.replace("-", "").replace(" ", "").uppercase()
        require(cleaned.length == 10) { "Connection code must be 10 characters" }

        var value = 0L
        for (ch in cleaned) {
            val digit = DECODE_MAP[ch] ?: throw IllegalArgumentException("Invalid character: $ch")
            value = (value shl 5) or digit.toLong()
        }

        val port = (value and 0xFFFF).toInt()
        val ip3 = ((value ushr 16) and 0xFF).toInt()
        val ip2 = ((value ushr 24) and 0xFF).toInt()
        val ip1 = ((value ushr 32) and 0xFF).toInt()
        val ip0 = ((value ushr 40) and 0xFF).toInt()

        require(port in 1..65535) { "Invalid connection code: port out of range" }

        return "$ip0.$ip1.$ip2.$ip3" to port
    }
}
