package com.crosspaste.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class MurmurHash3Test {

    @Test
    fun testHash() {
        val hash = getCodecsUtils().hash("test".toByteArray())
        val streamingMurmurHash3 = StreamingMurmurHash3(seed = 13043025u)
        streamingMurmurHash3.update("test".toByteArray())
        val (hash1, hash2) = streamingMurmurHash3.finish()
        val streamHash =
            buildString(32) {
                appendHex(hash1)
                appendHex(hash2)
            }
        assertEquals(hash, streamHash, "Hashes should match")
    }
}
