package com.crosspaste.dto

import com.crosspaste.app.AppInfo
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.paste.SyncPasteCollection
import com.crosspaste.dto.paste.SyncPasteData
import com.crosspaste.dto.paste.SyncPasteLabel
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.dto.secure.PairingRequest
import com.crosspaste.dto.secure.PairingResponse
import com.crosspaste.dto.secure.TrustRequest
import com.crosspaste.dto.secure.TrustResponse
import com.crosspaste.dto.sync.EndpointInfo
import com.crosspaste.dto.sync.SyncInfo
import com.crosspaste.paste.item.CreatePasteItemHelper.createTextPasteItem
import com.crosspaste.platform.Platform
import com.crosspaste.utils.getJsonUtils
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DtoSerializationTest {

    private val json = getJsonUtils().JSON

    private val testPlatform =
        Platform(
            name = "TestOS",
            arch = "x86_64",
            bitMode = 64,
            version = "1.0.0",
        )

    // --- PullFileRequest ---

    @Test
    fun `PullFileRequest serialization roundtrip`() {
        val original = PullFileRequest(id = 42L, chunkIndex = 7)
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PullFileRequest>(encoded)
        assertEquals(42L, decoded.id)
        assertEquals(7, decoded.chunkIndex)
    }

    @Test
    fun `PullFileRequest toString contains fields`() {
        val req = PullFileRequest(id = 1, chunkIndex = 0)
        val str = req.toString()
        assertTrue(str.contains("id=1"))
        assertTrue(str.contains("chunkIndex=0"))
    }

    // --- EndpointInfo ---

    @Test
    fun `EndpointInfo serialization roundtrip`() {
        val original =
            EndpointInfo(
                deviceId = "device-1",
                deviceName = "TestDevice",
                platform = testPlatform,
                hostInfoList = listOf(HostInfo(24, "192.168.1.100")),
                port = 8080,
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<EndpointInfo>(encoded)

        assertEquals("device-1", decoded.deviceId)
        assertEquals("TestDevice", decoded.deviceName)
        assertEquals(testPlatform, decoded.platform)
        assertEquals(1, decoded.hostInfoList.size)
        assertEquals("192.168.1.100", decoded.hostInfoList[0].hostAddress)
        assertEquals(8080, decoded.port)
    }

    @Test
    fun `EndpointInfo with multiple hosts`() {
        val original =
            EndpointInfo(
                deviceId = "device-1",
                deviceName = "Multi",
                platform = testPlatform,
                hostInfoList =
                    listOf(
                        HostInfo(24, "192.168.1.1"),
                        HostInfo(16, "10.0.0.1"),
                    ),
                port = 9090,
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<EndpointInfo>(encoded)
        assertEquals(2, decoded.hostInfoList.size)
    }

    // --- SyncInfo ---

    @Test
    fun `SyncInfo serialization roundtrip`() {
        val original =
            SyncInfo(
                appInfo = AppInfo("app-1", "1.0.0", "rev", "user"),
                endpointInfo =
                    EndpointInfo(
                        deviceId = "dev-1",
                        deviceName = "Test",
                        platform = testPlatform,
                        hostInfoList = listOf(HostInfo(24, "192.168.1.1")),
                        port = 8080,
                    ),
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncInfo>(encoded)

        assertEquals("app-1", decoded.appInfo.appInstanceId)
        assertEquals("dev-1", decoded.endpointInfo.deviceId)
    }

    @Test
    fun `SyncInfo merge combines host lists and takes other info`() {
        val info1 =
            SyncInfo(
                appInfo = AppInfo("app-1", "1.0.0", "rev", "user"),
                endpointInfo =
                    EndpointInfo(
                        deviceId = "dev-1",
                        deviceName = "OldName",
                        platform = testPlatform,
                        hostInfoList = listOf(HostInfo(24, "192.168.1.1")),
                        port = 8080,
                    ),
            )
        val info2 =
            SyncInfo(
                appInfo = AppInfo("app-1", "2.0.0", "rev2", "user2"),
                endpointInfo =
                    EndpointInfo(
                        deviceId = "dev-1",
                        deviceName = "NewName",
                        platform = testPlatform,
                        hostInfoList = listOf(HostInfo(24, "192.168.1.2")),
                        port = 9090,
                    ),
            )

        val merged = info1.merge(info2)

        assertEquals("app-1", merged.appInfo.appInstanceId)
        assertEquals("2.0.0", merged.appInfo.appVersion)
        assertEquals("NewName", merged.endpointInfo.deviceName)
        assertEquals(9090, merged.endpointInfo.port)
        assertEquals(2, merged.endpointInfo.hostInfoList.size)
    }

    @Test
    fun `SyncInfo merge deduplicates hosts by address`() {
        val info1 =
            SyncInfo(
                appInfo = AppInfo("app-1", "1.0.0", "rev", "user"),
                endpointInfo =
                    EndpointInfo(
                        deviceId = "dev-1",
                        deviceName = "Test",
                        platform = testPlatform,
                        hostInfoList = listOf(HostInfo(24, "192.168.1.1")),
                        port = 8080,
                    ),
            )
        val info2 =
            SyncInfo(
                appInfo = AppInfo("app-1", "1.0.0", "rev", "user"),
                endpointInfo =
                    EndpointInfo(
                        deviceId = "dev-1",
                        deviceName = "Test",
                        platform = testPlatform,
                        hostInfoList = listOf(HostInfo(24, "192.168.1.1")), // same host
                        port = 8080,
                    ),
            )

        val merged = info1.merge(info2)
        assertEquals(1, merged.endpointInfo.hostInfoList.size)
    }

    // --- PairingRequest ---

    @Test
    fun `PairingRequest serialization roundtrip`() {
        val original =
            PairingRequest(
                signPublicKey = byteArrayOf(1, 2, 3),
                cryptPublicKey = byteArrayOf(4, 5, 6),
                token = 12345,
                timestamp = 1000L,
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PairingRequest>(encoded)

        assertContentEquals(byteArrayOf(1, 2, 3), decoded.signPublicKey)
        assertContentEquals(byteArrayOf(4, 5, 6), decoded.cryptPublicKey)
        assertEquals(12345, decoded.token)
        assertEquals(1000L, decoded.timestamp)
    }

    @Test
    fun `PairingRequest equals works correctly with byte arrays`() {
        val a = PairingRequest(byteArrayOf(1, 2), byteArrayOf(3, 4), 1, 100)
        val b = PairingRequest(byteArrayOf(1, 2), byteArrayOf(3, 4), 1, 100)
        val c = PairingRequest(byteArrayOf(1, 2), byteArrayOf(3, 5), 1, 100)

        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `PairingRequest hashCode consistent with equals`() {
        val a = PairingRequest(byteArrayOf(1, 2), byteArrayOf(3, 4), 1, 100)
        val b = PairingRequest(byteArrayOf(1, 2), byteArrayOf(3, 4), 1, 100)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- PairingResponse ---

    @Test
    fun `PairingResponse serialization roundtrip`() {
        val original =
            PairingResponse(
                signPublicKey = byteArrayOf(10, 20),
                cryptPublicKey = byteArrayOf(30, 40),
                timestamp = 2000L,
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<PairingResponse>(encoded)

        assertContentEquals(byteArrayOf(10, 20), decoded.signPublicKey)
        assertContentEquals(byteArrayOf(30, 40), decoded.cryptPublicKey)
        assertEquals(2000L, decoded.timestamp)
    }

    @Test
    fun `PairingResponse equals and hashCode`() {
        val a = PairingResponse(byteArrayOf(1), byteArrayOf(2), 100)
        val b = PairingResponse(byteArrayOf(1), byteArrayOf(2), 100)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- TrustRequest ---

    @Test
    fun `TrustRequest serialization roundtrip`() {
        val pairingRequest = PairingRequest(byteArrayOf(1), byteArrayOf(2), 42, 1000)
        val original =
            TrustRequest(
                pairingRequest = pairingRequest,
                signature = byteArrayOf(7, 8, 9),
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<TrustRequest>(encoded)

        assertEquals(pairingRequest, decoded.pairingRequest)
        assertContentEquals(byteArrayOf(7, 8, 9), decoded.signature)
    }

    @Test
    fun `TrustRequest equals and hashCode`() {
        val pr = PairingRequest(byteArrayOf(1), byteArrayOf(2), 1, 1)
        val a = TrustRequest(pr, byteArrayOf(3))
        val b = TrustRequest(pr, byteArrayOf(3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // --- TrustResponse ---

    @Test
    fun `TrustResponse serialization roundtrip`() {
        val pairingResponse = PairingResponse(byteArrayOf(10), byteArrayOf(20), 2000)
        val original =
            TrustResponse(
                pairingResponse = pairingResponse,
                signature = byteArrayOf(50, 60),
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<TrustResponse>(encoded)

        assertEquals(pairingResponse, decoded.pairingResponse)
        assertContentEquals(byteArrayOf(50, 60), decoded.signature)
    }

    // --- SyncPasteLabel ---

    @Test
    fun `SyncPasteLabel serialization roundtrip`() {
        val original = SyncPasteLabel(id = "label-1", color = 0xFF0000, text = "Important")
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPasteLabel>(encoded)

        assertEquals("label-1", decoded.id)
        assertEquals(0xFF0000, decoded.color)
        assertEquals("Important", decoded.text)
    }

    // --- SyncPasteCollection ---

    @Test
    fun `SyncPasteCollection serialization roundtrip`() {
        val textItem = createTextPasteItem(text = "test item")
        val original = SyncPasteCollection(pasteItems = listOf(textItem))
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPasteCollection>(encoded)

        assertEquals(1, decoded.pasteItems.size)
    }

    @Test
    fun `SyncPasteCollection empty items roundtrip`() {
        val original = SyncPasteCollection(pasteItems = listOf())
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPasteCollection>(encoded)

        assertTrue(decoded.pasteItems.isEmpty())
    }

    // --- SyncPasteData ---

    @Test
    fun `SyncPasteData serialization roundtrip`() {
        val textItem = createTextPasteItem(text = "sync content")
        val original =
            SyncPasteData(
                id = "sync-1",
                appInstanceId = "app-1",
                pasteId = 100L,
                pasteType = 0,
                source = "Chrome",
                size = textItem.size,
                hash = textItem.hash,
                favorite = true,
                pasteAppearItem = textItem,
                pasteCollection = SyncPasteCollection(listOf()),
                labels = setOf(SyncPasteLabel("l1", 0xFF, "tag")),
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPasteData>(encoded)

        assertEquals("sync-1", decoded.id)
        assertEquals("app-1", decoded.appInstanceId)
        assertEquals(100L, decoded.pasteId)
        assertEquals("Chrome", decoded.source)
        assertTrue(decoded.favorite)
        assertNotNull(decoded.pasteAppearItem)
        assertEquals(1, decoded.labels.size)
    }

    @Test
    fun `SyncPasteData with null optional fields`() {
        val original =
            SyncPasteData(
                id = "sync-2",
                appInstanceId = "app-1",
                pasteId = 1L,
                pasteType = 0,
                source = null,
                size = 0L,
                hash = "",
                favorite = false,
                pasteAppearItem = null,
                pasteCollection = null,
                labels = emptySet(),
            )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<SyncPasteData>(encoded)

        assertEquals(null, decoded.source)
        assertEquals(null, decoded.pasteAppearItem)
        assertEquals(null, decoded.pasteCollection)
        assertTrue(decoded.labels.isEmpty())
    }
}
