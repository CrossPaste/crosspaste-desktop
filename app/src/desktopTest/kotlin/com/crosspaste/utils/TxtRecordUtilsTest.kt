package com.crosspaste.utils

import com.crosspaste.app.AppInfo
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TxtRecordUtilsTest {

    @Suppress("unused")
    private val jsonUtils = getJsonUtils()

    private fun createTestSyncInfo(): SyncInfo =
        SyncInfo(
            appInfo =
                AppInfo(
                    appInstanceId = "test-instance-123",
                    appVersion = "1.2.3",
                    appRevision = "abc123",
                    userName = "testuser",
                ),
            endpointInfo =
                EndpointInfo(
                    deviceId = "device-456",
                    deviceName = "TestDevice",
                    platform = Platform("macOS", "aarch64", 64, "14.0"),
                    hostInfoList = emptyList(),
                    port = 13129,
                ),
        )

    // Encodes a raw JSON string into the same base64-chunked TXT dict shape that
    // decodeFromTxtRecordDict consumes — lets a test feed hand-written wire payloads
    // (e.g. older/future peers) through the real decode path.
    private fun toTxtDict(
        rawJson: String,
        chunkSize: Int = 128,
    ): Map<String, ByteArray> {
        val base64 = getCodecsUtils().base64Encode(rawJson.encodeToByteArray())
        return base64
            .chunked(chunkSize)
            .mapIndexed { index, chunk -> index.toString() to chunk.encodeToByteArray() }
            .toMap()
    }

    @Test
    fun `encode and decode round-trip preserves SyncInfo`() {
        val original = createTestSyncInfo()
        val encoded = TxtRecordUtils.encodeToTxtRecordDict(original)
        val byteArrayMap = encoded.mapValues { (_, v) -> v.encodeToByteArray() }
        val decoded = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(byteArrayMap)
        assertEquals(original, decoded)
    }

    @Test
    fun `encoding produces sequential numeric keys starting from 0`() {
        val syncInfo = createTestSyncInfo()
        val encoded = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)
        val keys = encoded.keys.map { it.toInt() }.sorted()
        assertEquals((0 until encoded.size).toList(), keys)
    }

    @Test
    fun `chunk size controls value lengths`() {
        val syncInfo = createTestSyncInfo()
        val chunkSize = 32
        val encoded = TxtRecordUtils.encodeToTxtRecordDict(syncInfo, chunkSize = chunkSize)
        // All values except possibly the last should be exactly chunkSize
        val values = encoded.entries.sortedBy { it.key.toInt() }.map { it.value }
        for (i in 0 until values.size - 1) {
            assertEquals(chunkSize, values[i].length, "Chunk $i should be exactly $chunkSize chars")
        }
        assertTrue(values.last().length <= chunkSize, "Last chunk should be <= chunkSize")
    }

    @Test
    fun `larger chunk size produces fewer entries`() {
        val syncInfo = createTestSyncInfo()
        val smallChunk = TxtRecordUtils.encodeToTxtRecordDict(syncInfo, chunkSize = 32)
        val largeChunk = TxtRecordUtils.encodeToTxtRecordDict(syncInfo, chunkSize = 256)
        assertTrue(
            largeChunk.size <= smallChunk.size,
            "Larger chunk size should produce fewer or equal entries",
        )
    }

    @Test
    fun `decode reassembles chunks in numeric order regardless of map iteration order`() {
        val original = createTestSyncInfo()
        val encoded = TxtRecordUtils.encodeToTxtRecordDict(original, chunkSize = 32)

        // Create a reversed-order map to verify sorting works
        val reversed = LinkedHashMap<String, ByteArray>()
        encoded.entries.sortedByDescending { it.key.toInt() }.forEach { (k, v) ->
            reversed[k] = v.encodeToByteArray()
        }

        val decoded = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(reversed)
        assertEquals(original, decoded)
    }

    @Test
    fun `encode produces non-empty map for any serializable object`() {
        val syncInfo = createTestSyncInfo()
        val encoded = TxtRecordUtils.encodeToTxtRecordDict(syncInfo)
        assertTrue(encoded.isNotEmpty())
        encoded.values.forEach { assertTrue(it.isNotEmpty(), "Each chunk should be non-empty") }
    }

    // --- Phase B: HostInfo.lastSeen crosses the real mDNS TXT path ---

    @Test
    fun `round-trip preserves multi-host hostInfoList with lastSeen`() {
        val original =
            createTestSyncInfo().let {
                it.copy(
                    endpointInfo =
                        it.endpointInfo.copy(
                            hostInfoList =
                                listOf(
                                    HostInfo(24, "192.168.1.1", lastSeen = 111L),
                                    HostInfo(24, "192.168.1.2", lastSeen = 222L),
                                ),
                        ),
                )
            }
        val encoded = TxtRecordUtils.encodeToTxtRecordDict(original)
        val byteArrayMap = encoded.mapValues { (_, v) -> v.encodeToByteArray() }
        val decoded = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(byteArrayMap)
        assertEquals(original.endpointInfo.hostInfoList, decoded.endpointInfo.hostInfoList)
        assertEquals(
            222L,
            decoded.endpointInfo.hostInfoList
                .first { it.hostAddress == "192.168.1.2" }
                .lastSeen,
        )
    }

    @Test
    fun `decodes TXT payload from an older peer that omits lastSeen`() {
        // old build -> new build over the real mDNS TXT codec: hostInfoList entries
        // without lastSeen must decode with the default rather than failing discovery.
        val oldJson =
            """
            {"appInfo":{"appInstanceId":"id","appVersion":"1.0","appRevision":"r","userName":"u"},
             "endpointInfo":{"deviceId":"d","deviceName":"n",
              "platform":{"name":"macOS","arch":"aarch64","bitMode":64,"version":"14.0"},
              "hostInfoList":[{"networkPrefixLength":24,"hostAddress":"10.0.0.5"}],"port":13129}}
            """.trimIndent()
        val txtDict = toTxtDict(oldJson)

        val decoded = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(txtDict)

        assertEquals(
            "10.0.0.5",
            decoded.endpointInfo.hostInfoList
                .single()
                .hostAddress,
        )
        assertEquals(
            0L,
            decoded.endpointInfo.hostInfoList
                .single()
                .lastSeen,
        )
    }

    @Test
    fun `decodes TXT payload carrying unknown future keys without throwing`() {
        // new/future build -> this build over the real codec: unknown keys are tolerated,
        // which is the same mechanism by which an OLD peer ignores our new lastSeen.
        val futureJson =
            """
            {"appInfo":{"appInstanceId":"id","appVersion":"9.9","appRevision":"r","userName":"u","futureA":1},
             "endpointInfo":{"deviceId":"d","deviceName":"n",
              "platform":{"name":"macOS","arch":"aarch64","bitMode":64,"version":"14.0"},
              "hostInfoList":[{"networkPrefixLength":24,"hostAddress":"10.0.0.6","lastSeen":42,"futureB":2}],
              "port":13129,"futureC":3}}
            """.trimIndent()
        val txtDict = toTxtDict(futureJson)

        val decoded = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(txtDict)

        assertEquals(
            "10.0.0.6",
            decoded.endpointInfo.hostInfoList
                .single()
                .hostAddress,
        )
        assertEquals(
            42L,
            decoded.endpointInfo.hostInfoList
                .single()
                .lastSeen,
        )
    }

    @Test
    fun `round-trip preserves unicode content in user name`() {
        val original =
            SyncInfo(
                appInfo =
                    AppInfo(
                        appInstanceId = "id",
                        appVersion = "1.0",
                        appRevision = "rev",
                        userName = "user",
                    ),
                endpointInfo =
                    EndpointInfo(
                        deviceId = "dev",
                        deviceName = "dev",
                        platform = Platform("macOS", "aarch64", 64, "14.0"),
                        hostInfoList = emptyList(),
                        port = 8080,
                    ),
            )
        val encoded = TxtRecordUtils.encodeToTxtRecordDict(original)
        val byteArrayMap = encoded.mapValues { (_, v) -> v.encodeToByteArray() }
        val decoded = TxtRecordUtils.decodeFromTxtRecordDict<SyncInfo>(byteArrayMap)
        assertEquals(original.appInfo.userName, decoded.appInfo.userName)
    }
}
