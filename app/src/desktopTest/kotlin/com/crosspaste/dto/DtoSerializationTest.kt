package com.crosspaste.dto

import com.crosspaste.app.AppInfo
import com.crosspaste.db.sync.HostInfo
import com.crosspaste.dto.paste.SyncPasteCollection
import com.crosspaste.dto.paste.SyncPasteData
import com.crosspaste.dto.paste.SyncPasteLabel
import com.crosspaste.dto.pull.PullFileRequest
import com.crosspaste.dto.pull.WsPullFileRequest
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

    // --- WsPullFileRequest ---
    // The sealed class uses @JsonClassDiscriminator("mode"); the discriminator is only emitted
    // when encoding via the parent-type serializer. If the Desktop side encodes a concrete
    // subtype directly, Chrome rejects the request with "only supports whole-file mode".

    @Test
    fun `WsPullFileRequest WholeFileRequest encodes mode discriminator via parent type`() {
        val request: WsPullFileRequest =
            WsPullFileRequest.WholeFileRequest(hash = "abc", fileName = "image.png")
        val encoded = json.encodeToString(request)
        assertTrue(
            encoded.contains("\"mode\":\"whole\""),
            "Expected discriminator 'mode:whole' in payload, got: $encoded",
        )
    }

    @Test
    fun `WsPullFileRequest ChunkRequest encodes mode discriminator via parent type`() {
        val request: WsPullFileRequest = WsPullFileRequest.ChunkRequest(id = 1L, chunkIndex = 0)
        val encoded = json.encodeToString(request)
        assertTrue(
            encoded.contains("\"mode\":\"chunk\""),
            "Expected discriminator 'mode:chunk' in payload, got: $encoded",
        )
    }

    @Test
    fun `WsPullFileRequest roundtrip preserves subtype`() {
        val whole: WsPullFileRequest =
            WsPullFileRequest.WholeFileRequest(hash = "h", fileName = "f.png")
        val decodedWhole = json.decodeFromString<WsPullFileRequest>(json.encodeToString(whole))
        assertTrue(decodedWhole is WsPullFileRequest.WholeFileRequest)
        assertEquals("h", decodedWhole.hash)
        assertEquals("f.png", decodedWhole.fileName)

        val chunk: WsPullFileRequest = WsPullFileRequest.ChunkRequest(id = 5L, chunkIndex = 2)
        val decodedChunk = json.decodeFromString<WsPullFileRequest>(json.encodeToString(chunk))
        assertTrue(decodedChunk is WsPullFileRequest.ChunkRequest)
        assertEquals(5L, decodedChunk.id)
        assertEquals(2, decodedChunk.chunkIndex)
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

    // --- HostInfo.mergeRecent (Phase B: recency-ordered, capacity-capped) ---

    @Test
    fun `mergeRecent orders incoming first and stamps now`() {
        val existing = listOf(HostInfo(24, "192.168.1.1", lastSeen = 100L))
        val incoming = listOf(HostInfo(24, "192.168.1.2"))

        val merged = HostInfo.mergeRecent(existing, incoming, now = 5000L)

        assertEquals(2, merged.size)
        assertEquals("192.168.1.2", merged[0].hostAddress)
        assertEquals(5000L, merged[0].lastSeen)
        assertEquals("192.168.1.1", merged[1].hostAddress)
        assertEquals(100L, merged[1].lastSeen)
    }

    @Test
    fun `mergeRecent dedups by address keeping fresh lastSeen`() {
        val existing = listOf(HostInfo(24, "192.168.1.1", lastSeen = 100L))
        val incoming = listOf(HostInfo(24, "192.168.1.1"))

        val merged = HostInfo.mergeRecent(existing, incoming, now = 5000L)

        assertEquals(1, merged.size)
        assertEquals("192.168.1.1", merged[0].hostAddress)
        assertEquals(5000L, merged[0].lastSeen)
    }

    @Test
    fun `mergeRecent caps at MAX_RECENT_HOST_INFO dropping oldest`() {
        // Existing already at the cap, each older than the next.
        val existing =
            (1..HostInfo.MAX_RECENT_HOST_INFO).map { i ->
                HostInfo(24, "10.0.0.$i", lastSeen = i.toLong())
            }
        // A brand-new address arrives.
        val incoming = listOf(HostInfo(24, "10.0.0.99"))

        val merged = HostInfo.mergeRecent(existing, incoming, now = 1_000L)

        assertEquals(HostInfo.MAX_RECENT_HOST_INFO, merged.size)
        // Newest is the incoming one.
        assertEquals("10.0.0.99", merged[0].hostAddress)
        // The oldest existing (lastSeen = 1, "10.0.0.1") was evicted.
        assertTrue(merged.none { it.hostAddress == "10.0.0.1" })
    }

    @Test
    fun `mergeRecent dedups incoming-internal duplicates`() {
        // incoming = [a, a, b] -> result must not contain duplicate a.
        val incoming =
            listOf(
                HostInfo(24, "10.0.0.1"),
                HostInfo(24, "10.0.0.1"),
                HostInfo(24, "10.0.0.2"),
            )

        val merged = HostInfo.mergeRecent(emptyList(), incoming, now = 100L)

        assertEquals(2, merged.size)
        assertEquals(1, merged.count { it.hostAddress == "10.0.0.1" })
        assertTrue(merged.any { it.hostAddress == "10.0.0.2" })
    }

    @Test
    fun `mergeRecent dedups duplicate addresses within incoming and does not crowd out history`() {
        // A peer (or a buggy/hostile TXT record) advertises the same address many times.
        // It must collapse to a single entry — not fill the cap and evict real history.
        val existing = listOf(HostInfo(24, "10.0.0.1", lastSeen = 1L))
        val incoming = List(HostInfo.MAX_RECENT_HOST_INFO) { HostInfo(24, "10.0.0.5") }

        val merged = HostInfo.mergeRecent(existing, incoming, now = 100L)

        assertEquals(2, merged.size)
        assertEquals(1, merged.count { it.hostAddress == "10.0.0.5" })
        assertTrue(merged.any { it.hostAddress == "10.0.0.1" })
    }

    @Test
    fun `mergeRecent output has no duplicate addresses even from legacy existing`() {
        // Legacy data could hold the same address twice (old .distinct() kept entries that
        // differed only by prefix). The merged result must still be address-unique.
        val existing =
            listOf(
                HostInfo(24, "10.0.0.7", lastSeen = 5L),
                HostInfo(32, "10.0.0.7", lastSeen = 3L),
            )
        val incoming = listOf(HostInfo(24, "10.0.0.8"))

        val merged = HostInfo.mergeRecent(existing, incoming, now = 100L)

        assertEquals(1, merged.count { it.hostAddress == "10.0.0.7" })
        assertEquals(merged.map { it.hostAddress }, merged.map { it.hostAddress }.distinct())
    }

    @Test
    fun `mergeRecent is capacity-only and never time-prunes a stale-but-present address`() {
        // An address with an ancient lastSeen survives as long as it fits in the cap —
        // there is no clock-based TTL (a stable device must never be aged out).
        val existing = listOf(HostInfo(24, "192.168.1.1", lastSeen = 1L))
        val incoming = listOf(HostInfo(24, "192.168.1.2"))

        val merged = HostInfo.mergeRecent(existing, incoming, now = 9_000_000L)

        assertTrue(merged.any { it.hostAddress == "192.168.1.1" })
    }

    // --- HostInfo cross-version wire compatibility (Phase B: lastSeen field) ---

    @Test
    fun `HostInfo decode from old wire without lastSeen uses default`() {
        // old build -> new build: a peer that predates the lastSeen field omits it.
        val oldWire = """{"networkPrefixLength":24,"hostAddress":"192.168.1.4"}"""
        val decoded = json.decodeFromString<HostInfo>(oldWire)
        assertEquals("192.168.1.4", decoded.hostAddress)
        assertEquals(24.toShort(), decoded.networkPrefixLength)
        assertEquals(0L, decoded.lastSeen)
    }

    @Test
    fun `HostInfo decode tolerates lastSeen plus unknown future keys`() {
        // new build -> old build: the configured Json (ignoreUnknownKeys=true) must not
        // throw on fields it doesn't know. This is the exact mechanism that lets an OLD
        // peer (same config) ignore our new lastSeen — guarding cross-version wire compat.
        val futureWire =
            """{"networkPrefixLength":24,"hostAddress":"192.168.1.4","lastSeen":7,"futureX":1}"""
        val decoded = json.decodeFromString<HostInfo>(futureWire)
        assertEquals("192.168.1.4", decoded.hostAddress)
        assertEquals(7L, decoded.lastSeen)
    }

    @Test
    fun `SyncInfo decode tolerates unknown future key on nested HostInfo`() {
        // Same tolerance must hold through the full SyncInfo envelope (the shape that
        // actually crosses the wire), not just a bare HostInfo.
        val wire =
            """
            {"appInfo":{"appInstanceId":"id","appVersion":"1.0.0","appRevision":"r","userName":"u"},
             "endpointInfo":{"deviceId":"d","deviceName":"n",
              "platform":{"name":"TestOS","arch":"x86_64","bitMode":64,"version":"1.0.0"},
              "hostInfoList":[{"networkPrefixLength":24,"hostAddress":"10.0.0.1","lastSeen":5,"futureX":1}],
              "port":13129}}
            """.trimIndent()
        val decoded = json.decodeFromString<SyncInfo>(wire)
        assertEquals(1, decoded.endpointInfo.hostInfoList.size)
        assertEquals("10.0.0.1", decoded.endpointInfo.hostInfoList[0].hostAddress)
        assertEquals(5L, decoded.endpointInfo.hostInfoList[0].lastSeen)
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
