package com.crosspaste.net

import com.crosspaste.db.sync.HostInfo
import com.crosspaste.sync.SyncTestFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SyncInfoHeaderCodecTest {

    @Test
    fun encodeThenDecode_roundTripsSyncInfo() {
        val syncInfo =
            SyncTestFixtures.createSyncInfo(
                hostInfoList =
                    listOf(
                        HostInfo(24, "192.168.1.11"),
                        HostInfo(64, "fe80::1"),
                    ),
            )
        val encoded = SyncInfoHeaderCodec.encode(syncInfo)
        assertEquals(syncInfo, SyncInfoHeaderCodec.decode(encoded))
    }

    @Test
    fun decode_garbage_returnsNull() {
        assertNull(SyncInfoHeaderCodec.decode("not-base64-or-json!!!"))
        assertNull(SyncInfoHeaderCodec.decode(""))
    }

    @Test
    fun decodeOrThrow_garbage_throws() {
        // decodeOrThrow preserves the failure so callers can log the cause.
        assertFailsWith<Throwable> { SyncInfoHeaderCodec.decodeOrThrow("not-base64-or-json!!!") }
    }
}
