package com.crosspaste.pairing.v3

/**
 * Deterministic binary encoder for pairing v3 security payloads.
 *
 * Wire format, all integers big-endian:
 * - the encoding starts with the domain separator: `[u32 length][utf8 bytes]`
 * - each field is `[u8 fieldId][u32 length][payload]`
 * - payloads: bytes as-is, strings as UTF-8, Int as 4 bytes, Long as 8 bytes,
 *   string lists as `[u32 count]` followed by `[u32 length][utf8 bytes]` per element
 *
 * Field ids are assigned by the encode functions in [PairingTranscriptCodec] and are
 * frozen by golden test vectors. This format is intentionally not self-describing:
 * both peers must know the exact schema for the bytes to verify.
 */
class CanonicalWriter(
    domain: String,
) {

    private val chunks = mutableListOf<ByteArray>()

    private var size = 0

    init {
        val domainBytes = domain.encodeToByteArray()
        append(u32(domainBytes.size))
        append(domainBytes)
    }

    fun field(
        id: Int,
        bytes: ByteArray,
    ): CanonicalWriter {
        require(id in 0..255) { "field id out of range: $id" }
        append(byteArrayOf(id.toByte()))
        append(u32(bytes.size))
        append(bytes)
        return this
    }

    fun field(
        id: Int,
        value: String,
    ): CanonicalWriter = field(id, value.encodeToByteArray())

    fun field(
        id: Int,
        value: Int,
    ): CanonicalWriter = field(id, u32(value))

    fun field(
        id: Int,
        value: Long,
    ): CanonicalWriter = field(id, u64(value))

    fun field(
        id: Int,
        values: List<String>,
    ): CanonicalWriter {
        val encodedValues = values.map { it.encodeToByteArray() }
        val payloadSize = 4 + encodedValues.sumOf { 4 + it.size }
        val payload = ByteArray(payloadSize)
        var offset = 0
        u32(values.size).copyInto(payload, offset)
        offset += 4
        for (encoded in encodedValues) {
            u32(encoded.size).copyInto(payload, offset)
            offset += 4
            encoded.copyInto(payload, offset)
            offset += encoded.size
        }
        return field(id, payload)
    }

    fun build(): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private fun append(bytes: ByteArray) {
        chunks.add(bytes)
        size += bytes.size
    }

    companion object {

        fun u32(value: Int): ByteArray =
            byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            )

        fun u64(value: Long): ByteArray =
            byteArrayOf(
                (value ushr 56).toByte(),
                (value ushr 48).toByte(),
                (value ushr 40).toByte(),
                (value ushr 32).toByte(),
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte(),
            )
    }
}
