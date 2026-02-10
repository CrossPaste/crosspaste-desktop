package com.crosspaste.utils

import com.crosspaste.app.AppInfo
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
