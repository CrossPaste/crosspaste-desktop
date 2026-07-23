package com.crosspaste.pairing.v3

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * RFC 5869 Appendix A test vectors for HKDF-SHA256.
 */
class HkdfTest {

    @Test
    fun testRfc5869Case1() =
        runTest {
            val ikm = ByteArray(22) { 0x0b }
            val salt = "000102030405060708090a0b0c".hexToByteArray()
            val info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray()

            val prk = PairingKeySchedule.hkdfExtract(salt, ikm)
            assertContentEquals(
                "077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5".hexToByteArray(),
                prk,
            )

            val okm = PairingKeySchedule.hkdfExpand(prk, info, 42)
            assertContentEquals(
                (
                    "3cb25f25faacd57a90434f64d0362f2a" +
                        "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                        "34007208d5b887185865"
                ).hexToByteArray(),
                okm,
            )
        }

    @Test
    fun testRfc5869Case2LongInputs() =
        runTest {
            val ikm = ByteArray(80) { it.toByte() }
            val salt = ByteArray(80) { (0x60 + it).toByte() }
            val info = ByteArray(80) { (0xb0 + it).toByte() }

            val prk = PairingKeySchedule.hkdfExtract(salt, ikm)
            assertContentEquals(
                "06a6b88c5853361a06104c9ceb35b45cef760014904671014a193f40c15fc244".hexToByteArray(),
                prk,
            )

            val okm = PairingKeySchedule.hkdfExpand(prk, info, 82)
            assertContentEquals(
                (
                    "b11e398dc80327a1c8e7f78c596a4934" +
                        "4f012eda2d4efad8a050cc4c19afa97c" +
                        "59045a99cac7827271cb41c65e590e09" +
                        "da3275600c2f09b8367793a9aca3db71" +
                        "cc30c58179ec3e87c14c01d5c1f3434f" +
                        "1d87"
                ).hexToByteArray(),
                okm,
            )
        }

    @Test
    fun testRfc5869Case3EmptySaltAndInfo() =
        runTest {
            val ikm = ByteArray(22) { 0x0b }

            val prk = PairingKeySchedule.hkdfExtract(ByteArray(0), ikm)
            assertContentEquals(
                "19ef24a32c717b167f33a91d6f648bdf96596776afdb6377ac434c1c293ccb04".hexToByteArray(),
                prk,
            )

            val okm = PairingKeySchedule.hkdfExpand(prk, ByteArray(0), 42)
            assertContentEquals(
                (
                    "8da4e775a563c18f715f802a063c5a31" +
                        "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                        "9d201395faa4b61a96c8"
                ).hexToByteArray(),
                okm,
            )
        }
}

internal fun String.hexToByteArray(): ByteArray {
    check(length % 2 == 0) { "hex string must have even length" }
    return ByteArray(length / 2) { i ->
        substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

internal fun ByteArray.toHexString(): String =
    joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
